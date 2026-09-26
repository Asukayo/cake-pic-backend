package com.sharkycake.proofing.service;

import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.digest.DigestUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sharkycake.common.exception.BusinessException;
import com.sharkycake.common.exception.ErrorCode;
import com.sharkycake.common.exception.ThrowUtils;
import com.sharkycake.infrastructure.cos.ProofingStorageManager;
import com.sharkycake.proofing.dto.ProofingProjectSelectRequest;
import com.sharkycake.proofing.entity.ProofingAsset;
import com.sharkycake.proofing.entity.ProofingItem;
import com.sharkycake.proofing.entity.ProofingProject;
import com.sharkycake.proofing.enums.ProjectStatus;
import com.sharkycake.proofing.mapper.ProofingProjectMapper;
import com.sharkycake.proofing.vo.ProofingAssetAccessVO;
import com.sharkycake.proofing.vo.ProofingPublicItemVO;
import com.sharkycake.proofing.vo.ProofingPublicProjectVO;
import com.sharkycake.proofing.vo.ProofingSelectVO;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static com.sharkycake.proofing.service.impl.ProofingProjectServiceImpl.PROOF_SHARING_PREFIX;
import static com.sharkycake.proofing.service.impl.ProofingProjectServiceImpl.PROOF_USER_SESSION_PREFIX;

/** 客户访问业务：查看选单、预览照片和选择照片。 */
@Service
public class ProofingPublicReadService {

    private static final int PREVIEW_TTL_SECONDS = 600;

    private final StringRedisTemplate redisTemplate;
    private final ProofingProjectService proofingProjectService;
    private final ProofingProjectMapper proofingProjectMapper;
    private final ProofingItemService proofingItemService;
    private final ProofingAssetService proofingAssetService;
    private final ProofingStorageManager proofingStorageManager;

    public ProofingPublicReadService(StringRedisTemplate redisTemplate,
                                     ProofingProjectService proofingProjectService,
                                     ProofingProjectMapper proofingProjectMapper,
                                     ProofingItemService proofingItemService,
                                     ProofingAssetService proofingAssetService, ProofingStorageManager proofingStorageManager) {
        this.redisTemplate = redisTemplate;
        this.proofingProjectService = proofingProjectService;
        this.proofingProjectMapper = proofingProjectMapper;
        this.proofingItemService = proofingItemService;
        this.proofingAssetService = proofingAssetService;
        this.proofingStorageManager = proofingStorageManager;
    }


    /** 使用客户会话读取当前项目，不接受客户端提供项目 ID。 */
    public ProofingPublicProjectVO getProject(String sessionToken) {
        // 校验token是否合法
        ProofingProject project = requireCustomerProject(sessionToken);
        // 查询当前project数据并返回,其中还有selectedCount需要查询Item表
        Long selected = proofingItemService.lambdaQuery().eq(ProofingItem::getProjectId, project.getId())
                .eq(ProofingItem::getSelected, 1).count();
        ProofingPublicProjectVO vo = new ProofingPublicProjectVO();
        vo.setProjectId(String.valueOf(project.getId()));
        vo.setTitle(project.getTitle());
        vo.setStatus(project.getStatus());
        vo.setSelectionLimit(project.getSelectionLimit());
        vo.setSelectedCount(selected);
        vo.setVersion(project.getVersion());
        return vo;
    }

    /** 返回当前页允许客户查看的明细和短时预览地址。 */
    public Page<ProofingPublicItemVO> listItems(String sessionToken, long page, long pageSize) {
        ThrowUtils.throwIf(page < 1 || pageSize < 1 || pageSize > 50,
                ErrorCode.PARAMS_ERROR, "分页参数不合法");
        // 依旧校验是否合法
        ProofingProject project = requireCustomerProject(sessionToken);
        int ttlSeconds = previewTtlSeconds(sessionToken, project.getId());
        // 分页查询当前页所有item数据
        Page<ProofingPublicItemVO> result = new Page<>(page, pageSize);
        Page<ProofingItem> itemPage = proofingItemService.lambdaQuery()
                .eq(ProofingItem::getProjectId, project.getId())
                // 如果选单确认后只展示已选照片；固定快照将在下一阶段替换这里的查询。
                .eq(!ProjectStatus.SELECTING.name().equals(project.getStatus()), ProofingItem::getSelected, 1)
                .orderByAsc(ProofingItem::getSortOrder, ProofingItem::getId)
                .page(new Page<>(page, pageSize));
        result.setTotal(itemPage.getTotal());
        result.setRecords(itemPage.getRecords()
                .stream()
                .map(item -> toPublicItemVO(item, project.getId(), ttlSeconds))
                .collect(Collectors.toList()));
        return result;
    }

    /** 客户预览地址过期后，只为当前项目中可见的一张照片重新签发。 */
    public ProofingAssetAccessVO signPreview(String sessionToken, Long assetId) {
        ThrowUtils.throwIf(assetId == null || assetId <= 0, ErrorCode.PARAMS_ERROR, "资产 ID 不合法");
        ProofingProject project = requireCustomerProject(sessionToken);
        // 资产必须仍被当前项目中客户可见的 item 引用。
        ProofingItem item = proofingItemService.lambdaQuery()
                .eq(ProofingItem::getProjectId, project.getId())
                .eq(ProofingItem::getPreviewAssetId, assetId)
                .eq(!ProjectStatus.SELECTING.name().equals(project.getStatus()), ProofingItem::getSelected, 1)
                .one();
        ThrowUtils.throwIf(item == null, ErrorCode.NOT_FOUND_ERROR, "预览图片不存在");
        int ttlSeconds = previewTtlSeconds(sessionToken, project.getId());
        return signVisiblePreview(item, project.getId(), ttlSeconds);
    }

    // 用于校验当前sessionToken是否合法
    private ProofingProject requireCustomerProject(String sessionToken) {
        ThrowUtils.throwIf(StrUtil.isBlank(sessionToken), ErrorCode.NO_AUTH_ERROR, "客户会话无效");
        // 转换为对应的摘要
        String session = DigestUtil.sha256Hex(sessionToken);
        // 先查看对应的session令牌有没有过期
        String redisSession
                = redisTemplate.opsForValue().get(PROOF_USER_SESSION_PREFIX + session);
        ThrowUtils.throwIf(redisSession == null, ErrorCode.NO_AUTH_ERROR, "客户会话已失效，请重新获取");
        // 从中解析出对应的projectId和projectId对应的当前摘要
        String[] parts = redisSession.split(":", -1);
        ThrowUtils.throwIf(parts.length != 2 || StrUtil.isBlank(parts[0]) || StrUtil.isBlank(parts[1]),
                ErrorCode.NO_AUTH_ERROR, "客户会话无效");
        Long projectId;
        try {
            projectId = Long.valueOf(parts[0]);
        } catch (NumberFormatException e) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "客户会话无效");
        }
        ThrowUtils.throwIf(projectId <= 0, ErrorCode.NO_AUTH_ERROR, "客户会话无效");
        String projectDigest = parts[1];
        // 先判断当前分享链接有没有失效
        String realDigest = redisTemplate.opsForValue().get(PROOF_SHARING_PREFIX + projectId);
        ThrowUtils.throwIf(realDigest == null || !realDigest.equals(projectDigest),
                ErrorCode.NO_AUTH_ERROR, "当前分享链接已失效");
        // 查看Project状态，如果不为CLose或者Draft那么校验成功
        ProofingProject project = proofingProjectService.getById(projectId);
        ThrowUtils.throwIf(project == null, ErrorCode.NOT_FOUND_ERROR, "项目不存在");
        String status = project.getStatus();
        boolean accessible = ProjectStatus.SELECTING.name().equals(status)
                || ProjectStatus.CONFIRMED.name().equals(status)
                || ProjectStatus.DELIVERED.name().equals(status);
        ThrowUtils.throwIf(!accessible, ErrorCode.NO_AUTH_ERROR, "当前项目不可访问");

        return project;
    }

    /** 签名时间不能长于客户会话、分享链接的剩余时间。 */
    private int previewTtlSeconds(String sessionToken, Long projectId) {
        String sessionKey = PROOF_USER_SESSION_PREFIX + DigestUtil.sha256Hex(sessionToken);
        Long sessionTtl = redisTemplate.getExpire(sessionKey, TimeUnit.SECONDS);
        Long shareTtl = redisTemplate.getExpire(PROOF_SHARING_PREFIX + projectId, TimeUnit.SECONDS);
        ThrowUtils.throwIf(sessionTtl == null || sessionTtl <= 0 || shareTtl == null || shareTtl <= 0,
                ErrorCode.NO_AUTH_ERROR, "客户会话或分享链接已失效");
        return (int) Math.min(PREVIEW_TTL_SECONDS, Math.min(sessionTtl, shareTtl));
    }

    private ProofingPublicItemVO toPublicItemVO(ProofingItem item, Long projectId, int ttlSeconds) {
        ProofingAssetAccessVO access = signVisiblePreview(item, projectId, ttlSeconds);

        ProofingPublicItemVO vo = new ProofingPublicItemVO();
        vo.setItemId(String.valueOf(item.getId()));
        vo.setPreviewAssetId(String.valueOf(item.getPreviewAssetId()));
        vo.setDisplayName(item.getDisplayName());
        vo.setSortOrder(item.getSortOrder());
        vo.setSelected(Objects.equals(item.getSelected(), 1));
        vo.setPreviewUrl(access.getUrl());
        vo.setPreviewUrlExpiresAt(access.getExpiresAt());
        return vo;
    }

    /** 列表和单张续签共用相同的资产校验与签名规则。 */
    private ProofingAssetAccessVO signVisiblePreview(ProofingItem item, Long projectId, int ttlSeconds) {
        ThrowUtils.throwIf(item == null || !Objects.equals(item.getProjectId(), projectId),
                ErrorCode.NOT_FOUND_ERROR, "预览图片不存在");
        ProofingAsset asset = item.getPreviewAssetId() == null
                ? null : proofingAssetService.getById(item.getPreviewAssetId());
        ThrowUtils.throwIf(asset == null
                        || !Objects.equals(asset.getProjectId(), projectId)
                        || !"PREVIEW".equals(asset.getKind())
                        || !"READY".equals(asset.getStatus()),
                ErrorCode.NOT_FOUND_ERROR, "预览图片不存在");

        ProofingAssetAccessVO access = new ProofingAssetAccessVO();
        access.setExpiresAt(new Date(System.currentTimeMillis() + ttlSeconds * 1000L));
        access.setUrl(proofingStorageManager.signGet(asset.getBucket(), asset.getObjectKey(), ttlSeconds));
        return access;
    }

    /** 在项目行锁内检查版本和选择上限，再原子更新图片与项目版本。 */
    @Transactional(rollbackFor = Exception.class)
    public ProofingSelectVO selectItem(String sessionToken, ProofingProjectSelectRequest selectRequest, Long itemId) {
        ThrowUtils.throwIf(itemId == null || itemId <= 0 || selectRequest == null
                        || selectRequest.getSelected() == null
                        || selectRequest.getExpectedVersion() == null
                        || selectRequest.getExpectedVersion() < 0,
                ErrorCode.PARAMS_ERROR, "图片 ID、选择状态或版本不合法");
        Boolean selected = selectRequest.getSelected();
        Long expectedVersion = selectRequest.getExpectedVersion();

        // 会话决定真实项目；项目行锁让同一选单的选片请求依次执行。
        ProofingProject sessionProject = requireCustomerProject(sessionToken);
        ProofingProject project = proofingProjectMapper.selectForUpdate(sessionProject.getId());
        ThrowUtils.throwIf(project == null, ErrorCode.NOT_FOUND_ERROR, "选单不存在");
        ThrowUtils.throwIf(!ProjectStatus.SELECTING.name().equals(project.getStatus()),
                new BusinessException(40902, "当前选单不能选片"));
        ThrowUtils.throwIf(!Objects.equals(project.getVersion(), expectedVersion),
                new BusinessException(40901, "选单版本已变化，请刷新后重试"));

        ProofingItem item = proofingItemService.lambdaQuery()
                .eq(ProofingItem::getProjectId, project.getId())
                .eq(ProofingItem::getId, itemId)
                .one();
        ThrowUtils.throwIf(item == null, ErrorCode.NOT_FOUND_ERROR, "图片不存在");
        long selectedCount = proofingItemService.lambdaQuery()
                .eq(ProofingItem::getProjectId, project.getId())
                .eq(ProofingItem::getSelected, 1)
                .count();
        boolean alreadySelected = Objects.equals(item.getSelected(), 1);

        // 相同状态是幂等请求，不重复递增版本；真正改变状态时才写两张表。
        if (alreadySelected != selected) {
            ThrowUtils.throwIf(selected && selectedCount >= project.getSelectionLimit(),
                    new BusinessException(40903, "已达到最多选择数量"));
            boolean itemUpdated = proofingItemService.lambdaUpdate()
                    .eq(ProofingItem::getId, itemId)
                    .eq(ProofingItem::getProjectId, project.getId())
                    .eq(ProofingItem::getSelected, alreadySelected ? 1 : 0)
                    .set(ProofingItem::getSelected, selected ? 1 : 0)
                    .update();
            ThrowUtils.throwIf(!itemUpdated, ErrorCode.OPERATION_ERROR, "更新图片选择状态失败");

            boolean versionUpdated = proofingProjectService.lambdaUpdate()
                    .eq(ProofingProject::getId, project.getId())
                    .eq(ProofingProject::getStatus, ProjectStatus.SELECTING.name())
                    .eq(ProofingProject::getVersion, expectedVersion)
                    .set(ProofingProject::getVersion, expectedVersion + 1)
                    .update();
            ThrowUtils.throwIf(!versionUpdated, new BusinessException(40901, "选单版本已变化，请刷新后重试"));
            selectedCount += selected ? 1 : -1;
            project.setVersion(expectedVersion + 1);
        }

        ProofingSelectVO vo = new ProofingSelectVO();
        vo.setOperateSuccess(true);
        vo.setSelected(selected);
        vo.setSelectedCount(selectedCount);
        vo.setLimitationLeft(project.getSelectionLimit() - (int) selectedCount);
        vo.setCurrentVersion(project.getVersion());
        return vo;
    }
}
