package com.sharkycake.proofing.upload;
import java.util.Date;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import com.sharkycake.common.exception.ErrorCode;
import com.sharkycake.common.exception.ThrowUtils;
import com.sharkycake.infrastructure.cos.CosConfig;
import com.sharkycake.proofing.auth.ProofingProjectAuthService;
import com.sharkycake.proofing.entity.ProofingAsset;
import com.sharkycake.proofing.entity.ProofingItem;
import com.sharkycake.proofing.entity.ProofingProject;
import com.sharkycake.proofing.enums.ProjectStatus;
import com.sharkycake.proofing.mapper.ProofingProjectMapper;
import com.sharkycake.proofing.service.ProofingAssetService;
import com.sharkycake.proofing.service.ProofingItemService;
import com.sharkycake.proofing.vo.ProofingPreviewUploadVO;
import com.sharkycake.space.constant.SpaceUserPermissionConstant;
import com.sharkycake.user.entity.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 预览上传的短事务操作；不在这里进行图片处理或 COS 请求。
 */
@Service
public class ProofingPreviewTxService {

    private final ProofingProjectMapper projectMapper;
    private final ProofingProjectAuthService authService;
    private final ProofingAssetService assetService;
    private final ProofingItemService itemService;
    private final CosConfig cosConfig;

    public ProofingPreviewTxService(ProofingProjectMapper projectMapper,
                                    ProofingProjectAuthService authService,
                                    ProofingAssetService assetService,
                                    ProofingItemService itemService,
                                    CosConfig cosConfig) {
        this.projectMapper = projectMapper;
        this.authService = authService;
        this.assetService = assetService;
        this.itemService = itemService;
        this.cosConfig = cosConfig;
    }

    @Transactional(rollbackFor = Exception.class)
    public ProofingAsset reservePreview(Long projectId, Long expectedVersion,
                                        User loginUser, PreparedPreview preview) {
        checkProjectStatus(projectId, expectedVersion, loginUser);
        // 该project已有的item数量
        long itemCount = itemService.lambdaQuery()
                .eq(ProofingItem::getProjectId, projectId)
                .count();
        // 该project中正在上传的item数量（未过期的 PREVIEW/STAGING Asset 数）
        long stagingCount = assetService.lambdaQuery()
                .eq(ProofingAsset::getProjectId, projectId)
                .eq(ProofingAsset::getKind, "PREVIEW")
                .eq(ProofingAsset::getStatus, "STAGING")
                .gt(ProofingAsset::getExpiresAt, new Date())
                .count();
        ThrowUtils.throwIf(itemCount + stagingCount >= 300,
                ErrorCode.OPERATION_ERROR, "该选单中图片数量超过限制");
        // 生成每次上传唯一的对象 Key，保存 PREVIEW/STAGING asset 和 expiresAt。
        ProofingAsset proofingAsset = new ProofingAsset();
        proofingAsset.setProjectId(projectId);
        proofingAsset.setUploadedBy(loginUser.getId());
        proofingAsset.setBucket(cosConfig.getProofingBucket());
        proofingAsset.setSizeBytes(preview.getSizeBytes());
        proofingAsset.setContentType(preview.getContentType());
        proofingAsset.setWidth(preview.getWidth());
        proofingAsset.setHeight(preview.getHeight());
        proofingAsset.setKind("PREVIEW");
        // 生成仅服务端可控的 Key；扩展名与重新编码后的 JPEG 保持一致。
        proofingAsset.setObjectKey("proofing/" + projectId + "/PREVIEW/" + UUID.randomUUID() + ".jpg");
        proofingAsset.setStatus("STAGING");
        proofingAsset.setSha256(preview.getSha256());
        // 设置一小时过期时间
        proofingAsset.setExpiresAt(new Date(System.currentTimeMillis() + TimeUnit.HOURS.toMillis(1)));
        proofingAsset.setCreateTime(new Date());
        proofingAsset.setUpdateTime(new Date());
        boolean save = assetService.save(proofingAsset);
        ThrowUtils.throwIf(!save,ErrorCode.OPERATION_ERROR,"创建asset失败");
        return proofingAsset;
    }

    @Transactional(rollbackFor = Exception.class)
    public ProofingPreviewUploadVO completePreview(Long projectId, Long assetId, Long expectedVersion,
                                                   User loginUser, PreparedPreview preview) {
        // 锁项目并重新校验真实归属、权限、DRAFT 和 expectedVersion。
        ProofingProject project = checkProjectStatus(projectId, expectedVersion, loginUser);
        // 检查 asset 属于本项目、kind=PREVIEW、status=STAGING 且尚未过期。
        ProofingAsset asset = assetService.getById(assetId);
        ThrowUtils.throwIf(asset == null,ErrorCode.OPERATION_ERROR,"不存在插入记录");
        ThrowUtils.throwIf(!Objects.equals(asset.getProjectId(), projectId)
                        || !"PREVIEW".equals(asset.getKind())
                        || !"STAGING".equals(asset.getStatus())
                        || asset.getExpiresAt() == null
                        || !asset.getExpiresAt().after(new Date()),
                ErrorCode.OPERATION_ERROR,"asset状态非法或已过期");
        ProofingItem lastItem = itemService.lambdaQuery()
                .eq(ProofingItem::getProjectId, projectId)
                .orderByDesc(ProofingItem::getSortOrder)
                .last("LIMIT 1")
                .one();
        // 在同一事务中插入 item、将 asset 改为 READY、project.version + 1。
        ProofingItem item = new ProofingItem();
        item.setProjectId(projectId);
        item.setPreviewAssetId(assetId);
        item.setDisplayName(preview.getDisplayName());
        item.setSortOrder(lastItem == null ? 1 : lastItem.getSortOrder() + 1);
        item.setSelected(0);
        item.setCreateTime(new Date());
        item.setUpdateTime(new Date());
        boolean save = itemService.save(item);
        ThrowUtils.throwIf(!save,ErrorCode.OPERATION_ERROR,"数据库操作失败");
        // 显式清空过期时间；条件更新避免覆盖已转为待删除的资产。
        boolean updateAsset = assetService.lambdaUpdate()
                .eq(ProofingAsset::getId, assetId)
                .eq(ProofingAsset::getProjectId, projectId)
                .eq(ProofingAsset::getStatus, "STAGING")
                .set(ProofingAsset::getStatus, "READY")
                .set(ProofingAsset::getExpiresAt, null)
                .update();
        ThrowUtils.throwIf(!updateAsset,ErrorCode.OPERATION_ERROR,"数据库操作失败");
        project.setVersion(project.getVersion() + 1);
        int update = projectMapper.updateById(project);
        ThrowUtils.throwIf(update == 0,ErrorCode.OPERATION_ERROR,"数据库操作失败");
        ProofingPreviewUploadVO result = new ProofingPreviewUploadVO();
        result.setItemId(String.valueOf(item.getId()));
        result.setPreviewAssetId(String.valueOf(assetId));
        result.setDisplayName(item.getDisplayName());
        result.setSortOrder(item.getSortOrder());
        result.setWidth(preview.getWidth());
        result.setHeight(preview.getHeight());
        result.setVersion(project.getVersion());
        return result;
    }

    /**
     * 锁项目并重新校验真实归属、权限、DRAFT 和 expectedVersion。
     */
    private ProofingProject checkProjectStatus(Long projectId, Long expectedVersion,
                                              User loginUser){
        ProofingProject project = projectMapper.selectForUpdate(projectId);
        ThrowUtils.throwIf(project == null,ErrorCode.OPERATION_ERROR,"不存在该项目");
        Long spaceId = project.getSpaceId();
        authService.requireSpacePermission(spaceId,loginUser, SpaceUserPermissionConstant.PROOFING_MANAGE);
        // 在项目行锁内检查 DRAFT 和 expectedVersion。
        ThrowUtils.throwIf(!project.getStatus().equals(ProjectStatus.DRAFT.name()),
                ErrorCode.OPERATION_ERROR,"当前选单状态不可更改");
        ThrowUtils.throwIf(!Objects.equals(project.getVersion(), expectedVersion),
                ErrorCode.OPERATION_ERROR,"当前选单已经被其他人操作过，请获取最新版本");
        return project;
    }

    @Transactional(rollbackFor = Exception.class)
    public void markDeletePendingIfStaging(Long assetId) {
        if (assetId == null) {
            return;
        }
        // item 插入与 STAGING→READY 同事务提交；仅状态条件就能保护补偿竞争。
        assetService.lambdaUpdate()
                .eq(ProofingAsset::getId, assetId)
                .eq(ProofingAsset::getKind, "PREVIEW")
                .eq(ProofingAsset::getStatus, "STAGING")
                .set(ProofingAsset::getStatus, "DELETE_PENDING")
                .update();
        // COS 删除由后续清理任务执行。
    }
}
