package com.sharkycake.proofing.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.sharkycake.common.exception.ErrorCode;
import com.sharkycake.common.exception.ThrowUtils;
import com.sharkycake.infrastructure.cos.ProofingStorageManager;
import com.sharkycake.proofing.auth.ProofingProjectAuthService;
import com.sharkycake.proofing.entity.ProofingAsset;
import com.sharkycake.proofing.entity.ProofingItem;
import com.sharkycake.proofing.service.ProofingAssetService;
import com.sharkycake.proofing.mapper.ProofingAssetMapper;
import com.sharkycake.proofing.service.ProofingItemService;
import com.sharkycake.proofing.vo.ProofingAssetAccessVO;
import com.sharkycake.space.constant.SpaceUserPermissionConstant;
import com.sharkycake.user.entity.User;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.Objects;

/**
* @author shark
* @description 针对表【proofing_asset】的数据库操作Service实现
* @createDate 2026-09-25 09:33:36
*/
@Service
public class ProofingAssetServiceImpl extends ServiceImpl<ProofingAssetMapper, ProofingAsset>
    implements ProofingAssetService{

    private static final int ACCESS_TTL_SECONDS = 120;

    private final ProofingProjectAuthService projectAuthService;
    private final ProofingItemService itemService;
    private final ProofingStorageManager storageManager;

    public ProofingAssetServiceImpl(ProofingProjectAuthService projectAuthService,
                                    ProofingItemService itemService,
                                    ProofingStorageManager storageManager) {
        this.projectAuthService = projectAuthService;
        this.itemService = itemService;
        this.storageManager = storageManager;
    }

    /**
     * 校验用户权限，并返回短时id
     */
    @Override
    public ProofingAssetAccessVO signEmployeePreview(Long projectId, Long assetId, User loginUser) {
        ThrowUtils.throwIf(projectId == null || projectId <= 0 || assetId == null || assetId <= 0,
                ErrorCode.PARAMS_ERROR, "项目或资产 ID 不合法");
        // 从项目读取真实空间归属，再检查当前员工是否有查看权限。
        projectAuthService.requireProjectPermission(
                projectId, loginUser, SpaceUserPermissionConstant.PROOFING_VIEW);

        ProofingAsset asset = this.getById(assetId);
        ThrowUtils.throwIf(asset == null
                        || !Objects.equals(asset.getProjectId(), projectId)
                        || !"PREVIEW".equals(asset.getKind())
                        || !"READY".equals(asset.getStatus()),
                ErrorCode.NOT_FOUND_ERROR, "预览图片不存在");

        // 仅签发仍被该项目 item 引用的预览，不能访问孤立资产。
        boolean linked = itemService.lambdaQuery()
                .eq(ProofingItem::getProjectId, projectId)
                .eq(ProofingItem::getPreviewAssetId, assetId)
                .count() > 0;
        ThrowUtils.throwIf(!linked, ErrorCode.NOT_FOUND_ERROR, "预览图片不存在");

        ProofingAssetAccessVO result = new ProofingAssetAccessVO();
        result.setExpiresAt(new Date(System.currentTimeMillis() + ACCESS_TTL_SECONDS * 1000L));
        result.setUrl(storageManager.signGet(asset.getBucket(), asset.getObjectKey(), ACCESS_TTL_SECONDS));
        return result;
    }
}




