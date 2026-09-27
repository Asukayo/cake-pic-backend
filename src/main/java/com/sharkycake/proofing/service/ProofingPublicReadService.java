package com.sharkycake.proofing.service;

import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.digest.DigestUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sharkycake.common.exception.BusinessException;
import com.sharkycake.common.exception.ErrorCode;
import com.sharkycake.common.exception.ThrowUtils;
import com.sharkycake.infrastructure.cos.ProofingStorageManager;
import com.sharkycake.proofing.dto.ProofingAnnotation;
import com.sharkycake.proofing.dto.ProofingAnnotationRequest;
import com.sharkycake.proofing.dto.ProofingConfirmRequest;
import com.sharkycake.proofing.dto.ProofingManifest;
import com.sharkycake.proofing.dto.ProofingProjectSelectRequest;
import com.sharkycake.proofing.entity.ProofingAsset;
import com.sharkycake.proofing.entity.ProofingItem;
import com.sharkycake.proofing.entity.ProofingProject;
import com.sharkycake.proofing.entity.ProofingSubmission;
import com.sharkycake.proofing.enums.ProjectStatus;
import com.sharkycake.proofing.mapper.ProofingProjectMapper;
import com.sharkycake.proofing.vo.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
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
    private final ObjectMapper objectMapper;
    private final ProofingSubmissionService proofingSubmissionService;

    public ProofingPublicReadService(StringRedisTemplate redisTemplate,
                                     ProofingProjectService proofingProjectService,
                                     ProofingProjectMapper proofingProjectMapper,
                                     ProofingItemService proofingItemService,
                                     ProofingAssetService proofingAssetService,
                                     ProofingStorageManager proofingStorageManager,
                                     ObjectMapper objectMapper, ProofingSubmissionService proofingSubmissionService) {
        this.redisTemplate = redisTemplate;
        this.proofingProjectService = proofingProjectService;
        this.proofingProjectMapper = proofingProjectMapper;
        this.proofingItemService = proofingItemService;
        this.proofingAssetService = proofingAssetService;
        this.proofingStorageManager = proofingStorageManager;
        this.objectMapper = objectMapper;
        this.proofingSubmissionService = proofingSubmissionService;
    }


    /** 使用客户会话读取当前项目，不接受客户端提供项目 ID。 */
    public ProofingPublicProjectVO getProject(String sessionToken) {
        ProofingProject project = requireCustomerProject(sessionToken);
        Long selected = ProjectStatus.SELECTING.name().equals(project.getStatus())
                ? proofingItemService.lambdaQuery().eq(ProofingItem::getProjectId, project.getId())
                    .eq(ProofingItem::getSelected, 1).count()
                : (long) proofingSubmissionService.getSnapshot(project.getId()).getItems().size();
        ProofingPublicProjectVO vo = new ProofingPublicProjectVO();
        vo.setProjectId(String.valueOf(project.getId()));
        vo.setTitle(project.getTitle());
        vo.setStatus(project.getStatus());
        vo.setSelectionLimit(project.getSelectionLimit());
        vo.setSelectedCount(selected);
        vo.setVersion(project.getVersion());
        return vo;
    }

    /** 客户会话只允许读取所属项目的确认快照。 */
    public ProofingSubmissionVO getSubmission(String sessionToken) {
        ProofingProject project = requireCustomerProject(sessionToken);
        ThrowUtils.throwIf(ProjectStatus.SELECTING.name().equals(project.getStatus()),
                new BusinessException(40902, "选单尚未确认"));
        return proofingSubmissionService.getSnapshot(project.getId());
    }

    /** 返回当前页允许客户查看的明细和短时预览地址。 */
    public Page<ProofingPublicItemVO> listItems(String sessionToken, long page, long pageSize) {
        ThrowUtils.throwIf(page < 1 || pageSize < 1 || pageSize > 50,
                ErrorCode.PARAMS_ERROR, "分页参数不合法");
        // 依旧校验是否合法
        ProofingProject project = requireCustomerProject(sessionToken);
        int ttlSeconds = previewTtlSeconds(sessionToken, project.getId());
        Page<ProofingPublicItemVO> result = new Page<>(page, pageSize);
        if (!ProjectStatus.SELECTING.name().equals(project.getStatus())) {
            // 确认后只按 manifest 的固定顺序、字段和批注返回照片。
            List<ProofingManifest.Item> items = proofingSubmissionService
                    .getSnapshot(project.getId()).getItems();
            result.setTotal(items.size());
            if (items.isEmpty() || page > (items.size() - 1) / pageSize + 1) {
                result.setRecords(new ArrayList<>());
                return result;
            }
            int from = (int) ((page - 1) * pageSize);
            int to = (int) Math.min(from + pageSize, items.size());
            List<ProofingPublicItemVO> records = new ArrayList<>(to - from);
            for (int i = from; i < to; i++) {
                records.add(toPublicItemVO(items.get(i), i, project.getId(), ttlSeconds));
            }
            result.setRecords(records);
            return result;
        }

        // 选择中仍读取实时 item，供客户继续选片和修改批注。
        Page<ProofingItem> itemPage = proofingItemService.lambdaQuery()
                .eq(ProofingItem::getProjectId, project.getId())
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
        int ttlSeconds = previewTtlSeconds(sessionToken, project.getId());
        if (!ProjectStatus.SELECTING.name().equals(project.getStatus())) {
            boolean visible = proofingSubmissionService.getSnapshot(project.getId()).getItems()
                    .stream().anyMatch(item -> String.valueOf(assetId).equals(item.getPreviewAssetId()));
            ThrowUtils.throwIf(!visible, ErrorCode.NOT_FOUND_ERROR, "预览图片不存在");
            return signPreviewAsset(assetId, project.getId(), ttlSeconds);
        }
        // 选择中，资产必须仍被当前项目中的 item 引用。
        ProofingItem item = proofingItemService.lambdaQuery()
                .eq(ProofingItem::getProjectId, project.getId())
                .eq(ProofingItem::getPreviewAssetId, assetId)
                .one();
        ThrowUtils.throwIf(item == null, ErrorCode.NOT_FOUND_ERROR, "预览图片不存在");
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
        // 增加Annotation
        vo.setAnnotation(parseObject(item.getAnnotation(),ProofingAnnotation.class));
        return vo;
    }

    private ProofingPublicItemVO toPublicItemVO(ProofingManifest.Item item, int index,
                                                Long projectId, int ttlSeconds) {
        Long assetId;
        try {
            assetId = Long.valueOf(item.getPreviewAssetId());
        } catch (NumberFormatException | NullPointerException e) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "确认清单中的预览资产 ID 无效");
        }
        ProofingAssetAccessVO access = signPreviewAsset(assetId, projectId, ttlSeconds);
        ProofingPublicItemVO vo = new ProofingPublicItemVO();
        vo.setItemId(item.getItemId());
        vo.setPreviewAssetId(item.getPreviewAssetId());
        vo.setDisplayName(item.getDisplayName());
        vo.setSortOrder(index);
        vo.setSelected(true);
        vo.setPreviewUrl(access.getUrl());
        vo.setPreviewUrlExpiresAt(access.getExpiresAt());
        vo.setAnnotation(item.getAnnotation());
        return vo;
    }

    /** 列表和单张续签共用相同的资产校验与签名规则。 */
    private ProofingAssetAccessVO signVisiblePreview(ProofingItem item, Long projectId, int ttlSeconds) {
        ThrowUtils.throwIf(item == null || !Objects.equals(item.getProjectId(), projectId),
                ErrorCode.NOT_FOUND_ERROR, "预览图片不存在");
        return signPreviewAsset(item.getPreviewAssetId(), projectId, ttlSeconds);
    }

    private ProofingAssetAccessVO signPreviewAsset(Long assetId, Long projectId, int ttlSeconds) {
        ProofingAsset asset = assetId == null ? null : proofingAssetService.getById(assetId);
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
                    // 取消选片时，同一次更新清除草稿批注。
                    .set(!selected, ProofingItem::getAnnotation, null)
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

    /**
     * 为单个图片添加itemId
     */
    @Transactional(rollbackFor = Exception.class)
    public ProofingAnnotationVO addAnnotation(
            Long itemId, String sessionToken, ProofingAnnotationRequest request) {
        ThrowUtils.throwIf(itemId == null || itemId <= 0 || request == null
                        || request.getExpectedVersion() == null
                        || request.getExpectedVersion() < 0,
                ErrorCode.PARAMS_ERROR, "图片 ID 或版本不合法");
        ProofingProject project = requireCustomerProject(sessionToken);
        ProofingAnnotation annotation = normalizeAnnotation(request.getAnnotation());
        String json = annotation == null ? null : toAnnotationJson(annotation);
        Long version = request.getExpectedVersion();

        // 第一条数据库写入锁住项目；失败统一提示刷新。
        boolean advanced = proofingProjectService.lambdaUpdate()
                .eq(ProofingProject::getId, project.getId())
                .eq(ProofingProject::getStatus, ProjectStatus.SELECTING.name())
                .eq(ProofingProject::getVersion, version)
                .set(ProofingProject::getVersion, version + 1)
                .update();
        ThrowUtils.throwIf(!advanced,
                new BusinessException(40901, "选单状态或版本已变化，请刷新后重试"));

        ProofingItem item = proofingItemService.lambdaQuery()
                .eq(ProofingItem::getProjectId, project.getId())
                .eq(ProofingItem::getId, itemId)
                .eq(ProofingItem::getSelected, 1)
                .one();
        ThrowUtils.throwIf(item == null, ErrorCode.NOT_FOUND_ERROR, "已选图片不存在");

        // 相同内容也允许保存；SQL 异常会使整个事务回滚。
        proofingItemService.lambdaUpdate()
                .eq(ProofingItem::getProjectId, project.getId())
                .eq(ProofingItem::getId, itemId)
                .eq(ProofingItem::getSelected, 1)
                .set(ProofingItem::getAnnotation, json)
                .update();

        ProofingAnnotationVO vo = new ProofingAnnotationVO();
        vo.setAnnotation(annotation);
        vo.setCurrentVersion(version + 1);
        return vo;
    }


    /**
     * 标准化前端传来的注解
     */
    private ProofingAnnotation normalizeAnnotation(ProofingAnnotation annotation) {
        if (annotation == null) {
            return null; // 明确清空批注
        }

        String text = annotation.getText() == null
                ? null : annotation.getText().strip();
        ThrowUtils.throwIf(text == null || text.isEmpty()
                        || text.codePointCount(0, text.length()) > 500,
                ErrorCode.PARAMS_ERROR, "批注文字需为 1～500 字");
        annotation.setText(text);

        ProofingAnnotation.Rect rect = annotation.getRect();
        if (rect != null) {
            Double x = rect.getX(), y = rect.getY();
            Double w = rect.getW(), h = rect.getH();
            ThrowUtils.throwIf(x == null || y == null || w == null || h == null
                            || !Double.isFinite(x) || !Double.isFinite(y)
                            || !Double.isFinite(w) || !Double.isFinite(h)
                            || x < 0 || y < 0 || w <= 0 || h <= 0
                            || x + w > 1 || y + h > 1,
                    ErrorCode.PARAMS_ERROR, "矩形坐标不合法");
        }
        return annotation;
    }

    private  <T> T parseObject(String json,Class<T> clazz) {
        if (json == null) {
            return null;
        }
        try {
            return objectMapper.readValue(json, clazz);
        } catch (JsonProcessingException e) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "已保存的 JSON 无法解析");
        }
    }

    private String toAnnotationJson(ProofingAnnotation annotation) {
        try {
            return objectMapper.writeValueAsString(annotation);
        } catch (JsonProcessingException e) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "批注无法序列化");
        }
    }

    /** 锁定项目后，在同一事务固定清单并推进项目状态。 */
    @Transactional(rollbackFor = Exception.class)
    public ProofingConfirmVO confirmProofing(String sessionToken, ProofingConfirmRequest confirmRequest) {
        ThrowUtils.throwIf(confirmRequest == null
                        || StrUtil.isBlank(confirmRequest.getRequestId())
                        || confirmRequest.getRequestId().length() > 64
                        || confirmRequest.getExpectedVersion() == null
                        || confirmRequest.getExpectedVersion() < 0,
                ErrorCode.PARAMS_ERROR, "确认参数不合法");
        // 锁住项目
        ProofingProject sessionProject = requireCustomerProject(sessionToken);
        ProofingProject locked = proofingProjectMapper.selectForUpdate(sessionProject.getId());
        ThrowUtils.throwIf(locked == null, ErrorCode.NOT_FOUND_ERROR, "选单不存在");
        // 查询当前选单状态
        String status = locked.getStatus();
        ThrowUtils.throwIf(!ProjectStatus.SELECTING.name().equals(status)
                        && !ProjectStatus.CONFIRMED.name().equals(status)
                        && !ProjectStatus.DELIVERED.name().equals(status),
                ErrorCode.NO_AUTH_ERROR, "当前选单不可访问");
        // 当前读避免可重复读事务沿用会话校验时建立的旧快照。
        ProofingSubmission submission = proofingSubmissionService.lambdaQuery()
                .eq(ProofingSubmission::getProjectId, locked.getId())
                .last("FOR UPDATE")
                .one();
        if (submission != null) {
            // 如果订单已被提交过
            ThrowUtils.throwIf(!Objects.equals(submission.getRequestId(), confirmRequest.getRequestId()),
                    new BusinessException(40904, "选单已由另一次请求确认"));
            // 查询对应选单
            ProofingManifest manifest = parseObject(submission.getManifestJson(), ProofingManifest.class);
            ThrowUtils.throwIf(manifest == null || manifest.getItems() == null,
                    ErrorCode.SYSTEM_ERROR, "确认清单不可读取");
            // 创建返回vo
            ProofingConfirmVO vo = new ProofingConfirmVO();
            vo.setSubmissionId(String.valueOf(submission.getId()));
            vo.setSelectedCount(manifest.getItems().size());
            vo.setConfirmedAt(submission.getConfirmedAt());
            return vo;
        }

        // 首次确认先核对状态和版本，再从数据库中的已选照片构造快照。
        ThrowUtils.throwIf(!ProjectStatus.SELECTING.name().equals(status),
                new BusinessException(40902, "当前选单不能确认"));
        Long expectedVersion = confirmRequest.getExpectedVersion();
        ThrowUtils.throwIf(!Objects.equals(locked.getVersion(), expectedVersion),
                new BusinessException(40901, "选单版本已变化，请刷新后重试"));
        // 选出最终被确认的订单
        List<ProofingItem> selectedItems = proofingItemService.lambdaQuery()
                .eq(ProofingItem::getProjectId, locked.getId())
                .eq(ProofingItem::getSelected, 1)
                .orderByAsc(ProofingItem::getSortOrder, ProofingItem::getId)
                .list();
        ThrowUtils.throwIf(locked.getSelectionLimit() == null
                        || locked.getSelectionLimit() < 1
                        || selectedItems.isEmpty()
                        || selectedItems.size() > locked.getSelectionLimit(),
                new BusinessException(40902, "已选照片数量不符合确认规则"));
        // 创建图片表
        List<ProofingManifest.Item> manifestItems = new ArrayList<>(selectedItems.size());
        for (ProofingItem item : selectedItems) {
            ProofingAsset preview = item.getPreviewAssetId() == null
                    ? null : proofingAssetService.getById(item.getPreviewAssetId());
            ThrowUtils.throwIf(preview == null
                            || !Objects.equals(preview.getProjectId(), locked.getId())
                            || !"PREVIEW".equals(preview.getKind())
                            || !"READY".equals(preview.getStatus())
                            || preview.getWidth() == null || preview.getWidth() <= 0
                            || preview.getHeight() == null || preview.getHeight() <= 0,
                    new BusinessException(40905, "已选照片的预览文件不可用"));
            ProofingManifest.Item manifestItem = new ProofingManifest.Item();
            manifestItem.setItemId(String.valueOf(item.getId()));
            manifestItem.setDisplayName(item.getDisplayName());
            manifestItem.setPreviewAssetId(String.valueOf(preview.getId()));
            manifestItem.setPreviewWidth(preview.getWidth());
            manifestItem.setPreviewHeight(preview.getHeight());
            manifestItem.setAnnotation(normalizeAnnotation(
                    parseObject(item.getAnnotation(), ProofingAnnotation.class)));
            manifestItems.add(manifestItem);
        }
        ProofingManifest manifest = new ProofingManifest();
        manifest.setItems(manifestItems);

        ProofingSubmission newSubmission = new ProofingSubmission();
        newSubmission.setProjectId(locked.getId());
        newSubmission.setRequestId(confirmRequest.getRequestId());
        try {
            newSubmission.setManifestJson(objectMapper.writeValueAsString(manifest));
        } catch (JsonProcessingException e) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "确认清单无法序列化");
        }
        newSubmission.setConfirmedAt(new Date());
        boolean saved = proofingSubmissionService.save(newSubmission);
        ThrowUtils.throwIf(!saved, ErrorCode.OPERATION_ERROR, "保存确认清单失败");

        boolean advanced = proofingProjectService.lambdaUpdate()
                .eq(ProofingProject::getId, locked.getId())
                .eq(ProofingProject::getStatus, ProjectStatus.SELECTING.name())
                .eq(ProofingProject::getVersion, expectedVersion)
                .set(ProofingProject::getStatus, ProjectStatus.CONFIRMED.name())
                .set(ProofingProject::getVersion, expectedVersion + 1)
                .update();
        ThrowUtils.throwIf(!advanced,
                new BusinessException(40901, "选单状态或版本已变化，请刷新后重试"));

        ProofingConfirmVO vo = new ProofingConfirmVO();
        vo.setSubmissionId(String.valueOf(newSubmission.getId()));
        vo.setSelectedCount(manifestItems.size());
        vo.setConfirmedAt(newSubmission.getConfirmedAt());
        return vo;
    }
}
