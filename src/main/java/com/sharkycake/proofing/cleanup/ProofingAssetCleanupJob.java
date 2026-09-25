package com.sharkycake.proofing.cleanup;


import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 定时回收过期或上传后关联失败的预览资产。 */
@Component
public class ProofingAssetCleanupJob {

    private final ProofingAssetCleanupService cleanupService;

    public ProofingAssetCleanupJob(
            ProofingAssetCleanupService cleanupService
    ) {
        this.cleanupService = cleanupService;
    }

    /** 每分钟先标记过期预留，再清理已标记的待删除对象。 */
    @Scheduled(fixedDelay = 60_000)
    public void cleanup() {
        cleanupService.flagExpiredAsset();
        cleanupService.cleanExpiredAsset();
    }

}
