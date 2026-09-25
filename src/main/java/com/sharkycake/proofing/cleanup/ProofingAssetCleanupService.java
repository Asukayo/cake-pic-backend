package com.sharkycake.proofing.cleanup;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sharkycake.infrastructure.cos.ProofingStorageManager;
import com.sharkycake.proofing.entity.ProofingAsset;
import com.sharkycake.proofing.service.ProofingAssetService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;

/**
 * 清理失败或超时的预览资产；数据库状态变更与 COS 请求分开执行。
 */
@Slf4j
@Service
public class ProofingAssetCleanupService {

    private final ProofingAssetService proofingAssetService;
    private final ProofingStorageManager storageManager;

    public ProofingAssetCleanupService(ProofingAssetService proofingAssetService,
                                       ProofingStorageManager storageManager) {
        this.proofingAssetService = proofingAssetService;
        this.storageManager = storageManager;
    }

    @Transactional(rollbackFor = Exception.class)
    public void flagExpiredAsset() {
        Date now = new Date();
        // 每轮最多处理 100 条；其余留给下一轮，避免一次定时任务占用过久。
        List<ProofingAsset> expired = proofingAssetService.lambdaQuery()
                .select(ProofingAsset::getId)
                .eq(ProofingAsset::getKind, "PREVIEW")
                .eq(ProofingAsset::getStatus, "STAGING")
                .le(ProofingAsset::getExpiresAt, now)
                .orderByAsc(ProofingAsset::getExpiresAt)
                .last("LIMIT 100")
                .list();

        for (ProofingAsset asset : expired) {
            // 和上传确认竞争时，只有仍为 STAGING 的一方能更新成功。
            proofingAssetService.lambdaUpdate()
                    .eq(ProofingAsset::getId, asset.getId())
                    .eq(ProofingAsset::getKind, "PREVIEW")
                    .eq(ProofingAsset::getStatus, "STAGING")
                    .le(ProofingAsset::getExpiresAt, now)
                    .set(ProofingAsset::getStatus, "DELETE_PENDING")
                    .update();
        }
    }

    /**
     * DELETE_PENDING 表示可以清理：已过期，或 COS 已成功但数据库关联失败。
     * COS 调用期间不持有数据库事务。
     */
    public void cleanExpiredAsset() {
        List<ProofingAsset> pending = proofingAssetService.lambdaQuery()
                .select(ProofingAsset::getId, ProofingAsset::getBucket, ProofingAsset::getObjectKey)
                .eq(ProofingAsset::getKind, "PREVIEW")
                .eq(ProofingAsset::getStatus, "DELETE_PENDING")
                .orderByAsc(ProofingAsset::getUpdateTime)
                .orderByAsc(ProofingAsset::getId)
                .last("LIMIT 100")
                .list();

        for (ProofingAsset asset : pending) {
            try {
                // 删除不存在的 Key 可重复执行；COS 成功后才删除数据库资产记录。
                storageManager.delete(asset.getBucket(), asset.getObjectKey());
                proofingAssetService.remove(new LambdaQueryWrapper<ProofingAsset>()
                        .eq(ProofingAsset::getId, asset.getId())
                        .eq(ProofingAsset::getKind, "PREVIEW")
                        .eq(ProofingAsset::getStatus, "DELETE_PENDING"));
            } catch (Exception e) {
                // 保留 DELETE_PENDING；下一轮重试，不能因一条失败中断整批。
                log.warn("清理选片预览资产失败，assetId={}", asset.getId(), e);
                try {
                    // 将失败项排到队尾，避免它每轮占据前 100 条而阻塞后续资产。
                    proofingAssetService.lambdaUpdate()
                            .eq(ProofingAsset::getId, asset.getId())
                            .eq(ProofingAsset::getKind, "PREVIEW")
                            .eq(ProofingAsset::getStatus, "DELETE_PENDING")
                            .set(ProofingAsset::getUpdateTime, new Date())
                            .update();
                } catch (Exception updateError) {
                    log.warn("更新待清理资产重试顺序失败，assetId={}", asset.getId(), updateError);
                }
            }
        }
    }
}
