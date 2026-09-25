package com.sharkycake.proofing.service.impl;
import java.util.Arrays;
import java.util.Date;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import cn.hutool.core.lang.UUID;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.sharkycake.common.exception.BusinessException;
import com.sharkycake.common.exception.ErrorCode;
import com.sharkycake.common.exception.ThrowUtils;
import com.sharkycake.proofing.auth.ProofingProjectAuthService;
import com.sharkycake.proofing.dto.ProofingProjectCloseRequest;
import com.sharkycake.proofing.dto.ProofingProjectCreateRequest;
import com.sharkycake.proofing.dto.ProofingProjectPublishRequest;
import com.sharkycake.proofing.dto.ProofingProjectQueryRequest;
import com.sharkycake.proofing.dto.ProofingProjectUpdateRequest;
import com.sharkycake.proofing.entity.ProofingAsset;
import com.sharkycake.proofing.entity.ProofingItem;
import com.sharkycake.proofing.entity.ProofingProject;
import com.sharkycake.proofing.enums.ProjectStatus;
import com.sharkycake.proofing.service.ProofingAssetService;
import com.sharkycake.proofing.service.ProofingItemService;
import com.sharkycake.proofing.service.ProofingProjectService;
import com.sharkycake.proofing.mapper.ProofingProjectMapper;
import com.sharkycake.proofing.vo.ProofingProjectVO;
import com.sharkycake.space.constant.SpaceUserPermissionConstant;
import com.sharkycake.user.entity.User;
import com.sharkycake.user.service.UserService;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.BeanWrapper;
import org.springframework.beans.BeanWrapperImpl;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.servlet.http.HttpServletRequest;

/**
* @author shark
* @description 针对表【proofing_project】的数据库操作Service实现
* @createDate 2026-09-24 16:07:53
*/
@Service
public class ProofingProjectServiceImpl extends ServiceImpl<ProofingProjectMapper, ProofingProject>
    implements ProofingProjectService{

    /**
     * 根据不同的dto运行不同的断言规则
     */
    private static final Map<String, Predicate<Object>> CREATE_RULES = Map.of(
            "spaceId", v -> v instanceof Long && (Long) v > 0,
            "title", v -> v instanceof String
                    && !((String) v).trim().isEmpty()
                    && ((String) v).length() <= 128,
            "selectionLimit", v -> v instanceof Integer
                    && (Integer) v >= 1 && (Integer) v <= 20
    );

    private static final Map<String, Predicate<Object>> QUERY_RULES = Map.of(
            "spaceId", v -> v instanceof Long && (Long) v > 0,
            "current", v -> v instanceof Integer && (Integer) v > 0,
            "pageSize", v -> v instanceof Integer && (Integer) v >= 1 && (Integer) v <= 50,
            "status", v -> v == null || v instanceof String
                    && Arrays.stream(ProjectStatus.values()).anyMatch(s -> s.name().equals(v))
    );

    private static final Map<String, Predicate<Object>> UPDATE_RULES = Map.of(
            "title", v -> v == null || v instanceof String
                    && !((String) v).trim().isEmpty() && ((String) v).length() <= 128,
            "selectionLimit", v -> v == null || v instanceof Integer
                    && (Integer) v >= 1 && (Integer) v <= 20,
            "expectedVersion", v -> v instanceof Long && (Long) v >= 0
    );

    private static final Map<String, Predicate<Object>> CLOSE_RULES = Map.of(
            "expectedVersion", v -> v instanceof Long && (Long) v >= 0
    );


    private final UserService userService;
    private final ProofingProjectAuthService proofingProjectAuthService;
    private final ProofingItemService itemService;
    private final ProofingAssetService assetService;

    public ProofingProjectServiceImpl(UserService userService,
                                      ProofingProjectAuthService proofingProjectAuthService,
                                      ProofingItemService itemService,
                                      ProofingAssetService assetService) {
        this.userService = userService;
        this.proofingProjectAuthService = proofingProjectAuthService;
        this.itemService = itemService;
        this.assetService = assetService;
    }

    /**
     * 用于新建图片选单
     */
    @Override
    public ProofingProjectVO create(ProofingProjectCreateRequest proofingProjectCreateRequest,
                                    HttpServletRequest httpServletRequest) throws Exception {
        // 进行参数校验
        validateDto(proofingProjectCreateRequest,CREATE_RULES);
        Long spaceId = proofingProjectCreateRequest.getSpaceId();
        String title = proofingProjectCreateRequest.getTitle();
        Integer selectionLimit = proofingProjectCreateRequest.getSelectionLimit();
        User loginUser = userService.getLoginUser(httpServletRequest);
        // 鉴权
        proofingProjectAuthService.requireSpacePermission(
                spaceId,loginUser,
                SpaceUserPermissionConstant.PROOFING_MANAGE);
        // 参数校验结束
        ProofingProject proofingProject = new ProofingProject();
        proofingProject.setSpaceId(spaceId);
        proofingProject.setCreatedBy(loginUser.getId());
        proofingProject.setTitle(title);
        proofingProject.setStatus(ProjectStatus.DRAFT.name());
        proofingProject.setSelectionLimit(selectionLimit);
        proofingProject.setPublicId(UUID.randomUUID().toString().replace("-", ""));
        proofingProject.setVersion(0L);
        proofingProject.setCreateTime(new Date());
        proofingProject.setUpdateTime(new Date());
        boolean save = this.save(proofingProject);
        ThrowUtils.throwIf(!save,ErrorCode.OPERATION_ERROR,"选单创建失败");
        return toVo(proofingProject);
    }

    /**
     * 查询多个选单项目
     */
    @Override
    public Page<ProofingProjectVO> listProjects(ProofingProjectQueryRequest queryRequest,
                                                HttpServletRequest httpServletRequest) {
        validateDto(queryRequest, QUERY_RULES);
        User loginUser = userService.getLoginUser(httpServletRequest);
        proofingProjectAuthService.requireSpacePermission(
                queryRequest.getSpaceId(), loginUser, SpaceUserPermissionConstant.PROOFING_VIEW);

        LambdaQueryWrapper<ProofingProject> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(ProofingProject::getSpaceId, queryRequest.getSpaceId())
                .eq(queryRequest.getStatus() != null, ProofingProject::getStatus, queryRequest.getStatus())
                .orderByDesc(ProofingProject::getCreateTime, ProofingProject::getId);
        Page<ProofingProject> projectPage = this.page(
                new Page<>(queryRequest.getCurrent(), queryRequest.getPageSize()), queryWrapper);
        Page<ProofingProjectVO> result = new Page<>(
                projectPage.getCurrent(), projectPage.getSize(), projectPage.getTotal());
        result.setRecords(projectPage.getRecords().stream().map(this::toVo).collect(Collectors.toList()));
        return result;
    }

    /**
     * 查询特定的选单项目
     */
    @Override
    public ProofingProjectVO getProject(Long projectId, HttpServletRequest httpServletRequest) {
        requireProjectId(projectId);
        User loginUser = userService.getLoginUser(httpServletRequest);
        ProofingProject project = proofingProjectAuthService.requireProjectPermission(
                projectId, loginUser, SpaceUserPermissionConstant.PROOFING_VIEW);
        return toVo(project);
    }

    /**
     * 更新选选单
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public ProofingProjectVO updateDraft(Long projectId, ProofingProjectUpdateRequest updateRequest,
                                          HttpServletRequest httpServletRequest) {
        // 参数校验
        requireProjectId(projectId);
        validateDto(updateRequest, UPDATE_RULES);
        ThrowUtils.throwIf(updateRequest.getTitle() == null && updateRequest.getSelectionLimit() == null,
                ErrorCode.PARAMS_ERROR, "至少填写一项修改内容");
        // 查看当前用户是否有修改project信息的权限
        User loginUser = userService.getLoginUser(httpServletRequest);
        // 使用当前读锁住当前行
        ProofingProject project = lockProject(projectId);
        proofingProjectAuthService.requireSpacePermission(
                project.getSpaceId(), loginUser, SpaceUserPermissionConstant.PROOFING_MANAGE);
        // 判断当前选单状态是否支持修改
        requireVersion(project, updateRequest.getExpectedVersion());
        ThrowUtils.throwIf(!ProjectStatus.DRAFT.name().equals(project.getStatus()),
                new BusinessException(40902, "只有草稿可以修改"));
        // 更新选单信息
        boolean changed = false;
        if (updateRequest.getTitle() != null
                && !Objects.equals(project.getTitle(), updateRequest.getTitle())) {
            project.setTitle(updateRequest.getTitle());
            changed = true;
        }
        if (updateRequest.getSelectionLimit() != null
                && !Objects.equals(project.getSelectionLimit(), updateRequest.getSelectionLimit())) {
            project.setSelectionLimit(updateRequest.getSelectionLimit());
            changed = true;
        }
        if (changed) {
            project.setVersion(project.getVersion() + 1);
            project.setUpdateTime(new Date());
            ThrowUtils.throwIf(!this.updateById(project), ErrorCode.OPERATION_ERROR, "修改选单失败");
        }
        return toVo(project);
    }

    /**
     * 草稿图片移除：数据库内原子删除 item、标记资产待清理并递增版本。
     * COS 删除交给定时任务，避免在数据库事务中等待网络请求。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long removeDraftItem(Long projectId, Long itemId, Long expectedVersion,
                                HttpServletRequest httpServletRequest) {
        // 权限校验
        requireProjectId(projectId);
        ThrowUtils.throwIf(itemId == null || itemId <= 0 || expectedVersion == null || expectedVersion < 0,
                ErrorCode.PARAMS_ERROR, "图片 ID 或版本不合法");
        User loginUser = userService.getLoginUser(httpServletRequest);

        // 与上传、发布共用项目行锁，避免草稿状态和版本在操作中变化。
        ProofingProject project = lockProject(projectId);
        proofingProjectAuthService.requireSpacePermission(
                project.getSpaceId(), loginUser, SpaceUserPermissionConstant.PROOFING_MANAGE);
        requireVersion(project, expectedVersion);
        ThrowUtils.throwIf(!ProjectStatus.DRAFT.name().equals(project.getStatus()),
                new BusinessException(40902, "只有草稿可以移除图片"));

        ProofingItem item = itemService.lambdaQuery()
                .eq(ProofingItem::getId, itemId)
                .eq(ProofingItem::getProjectId, projectId)
                .one();
        ThrowUtils.throwIf(item == null, ErrorCode.NOT_FOUND_ERROR, "图片不存在");

        // 只改本项目中该 item 引用的 READY 预览资产，失败时整笔事务回滚。
        boolean marked = assetService.lambdaUpdate()
                .eq(ProofingAsset::getId, item.getPreviewAssetId())
                .eq(ProofingAsset::getProjectId, projectId)
                .eq(ProofingAsset::getKind, "PREVIEW")
                .eq(ProofingAsset::getStatus, "READY")
                .set(ProofingAsset::getStatus, "DELETE_PENDING")
                .update();
        ThrowUtils.throwIf(!marked, ErrorCode.OPERATION_ERROR, "标记图片待清理失败");

        boolean removed = itemService.remove(new LambdaQueryWrapper<ProofingItem>()
                .eq(ProofingItem::getId, itemId)
                .eq(ProofingItem::getProjectId, projectId));
        ThrowUtils.throwIf(!removed, ErrorCode.OPERATION_ERROR, "移除图片失败");

        project.setVersion(project.getVersion() + 1);
        project.setUpdateTime(new Date());
        ThrowUtils.throwIf(!this.updateById(project), ErrorCode.OPERATION_ERROR, "更新选单版本失败");
        return project.getVersion();
    }

    /** 发布后预览明细冻结；后续上传、修改和移除都会因状态不再是 DRAFT 而被拒绝。 */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public ProofingProjectVO publish(Long projectId, ProofingProjectPublishRequest publishRequest,
                                     HttpServletRequest httpServletRequest) {
        // 权限校验
        requireProjectId(projectId);
        ThrowUtils.throwIf(publishRequest == null || publishRequest.getExpectedVersion() == null
                        || publishRequest.getExpectedVersion() < 0,
                ErrorCode.PARAMS_ERROR, "版本号不合法");
        User loginUser = userService.getLoginUser(httpServletRequest);

        // 锁项目行，与上传完成和草稿图片移除串行，保证发布时的图片数量稳定。
        ProofingProject project = lockProject(projectId);
        proofingProjectAuthService.requireSpacePermission(
                project.getSpaceId(), loginUser, SpaceUserPermissionConstant.PROOFING_MANAGE);
        requireVersion(project, publishRequest.getExpectedVersion());
        ThrowUtils.throwIf(!ProjectStatus.DRAFT.name().equals(project.getStatus()),
                new BusinessException(40902, "只有草稿可以发布"));

        // item 与 READY 预览资产在上传完成时同事务写入，草稿移除时也同事务删除。
        long itemCount = itemService.lambdaQuery()
                .eq(ProofingItem::getProjectId, projectId)
                .count();
        ThrowUtils.throwIf(itemCount == 0 || project.getSelectionLimit() > itemCount,
                ErrorCode.OPERATION_ERROR, "图片数量不足，无法发布选单");

        project.setStatus(ProjectStatus.SELECTING.name());
        project.setVersion(project.getVersion() + 1);
        project.setUpdateTime(new Date());
        ThrowUtils.throwIf(!this.updateById(project), ErrorCode.OPERATION_ERROR, "发布选单失败");
        return toVo(project);
    }

    /**
     * 关闭选单（选单流程结束）
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public ProofingProjectVO closeProject(Long projectId, ProofingProjectCloseRequest closeRequest,
                                           HttpServletRequest httpServletRequest) {
        // 参数校验
        requireProjectId(projectId);
        validateDto(closeRequest, CLOSE_RULES);
        // 获取登陆者身份信息
        User loginUser = userService.getLoginUser(httpServletRequest);
        // 锁住数据行
        ProofingProject project = lockProject(projectId);
        proofingProjectAuthService.requireSpacePermission(
                project.getSpaceId(), loginUser, SpaceUserPermissionConstant.PROOFING_CLOSE);
        // 判断数据版本是否一致
        requireVersion(project, closeRequest.getExpectedVersion());
        ThrowUtils.throwIf(ProjectStatus.CLOSED.name().equals(project.getStatus()),
                new BusinessException(40902, "选单已关闭"));
        project.setStatus(ProjectStatus.CLOSED.name());
        project.setVersion(project.getVersion() + 1);
        project.setUpdateTime(new Date());
        ThrowUtils.throwIf(!this.updateById(project), ErrorCode.OPERATION_ERROR, "关闭选单失败");
        return toVo(project);
    }

    private ProofingProject lockProject(Long projectId) {
        ProofingProject project = baseMapper.selectForUpdate(projectId);
        ThrowUtils.throwIf(project == null, ErrorCode.NOT_FOUND_ERROR, "选单不存在");
        return project;
    }

    private void requireProjectId(Long projectId) {
        ThrowUtils.throwIf(projectId == null || projectId <= 0, ErrorCode.PARAMS_ERROR, "项目 ID 不合法");
    }

    private void requireVersion(ProofingProject project, Long expectedVersion) {
        ThrowUtils.throwIf(!Objects.equals(project.getVersion(), expectedVersion),
                new BusinessException(40901, "选单版本已变化，请刷新后重试"));
    }

    private ProofingProjectVO toVo(ProofingProject project) {
        ProofingProjectVO result = new ProofingProjectVO();
        BeanUtils.copyProperties(project, result);
        // copyProperties不会自动把id复制过去，需要手动复制
        result.setProjectId(String.valueOf(project.getId()));
        return result;
    }


    /**
     * 用来对dto进行参数校验
     */
    private void validateDto(Object dto, Map<String, Predicate<Object>> rules) {
        ThrowUtils.throwIf(dto == null, ErrorCode.PARAMS_ERROR, "请求不能为空");
        BeanWrapper bean = new BeanWrapperImpl(dto);
        rules.forEach((field, rule) ->
                ThrowUtils.throwIf(
                        !bean.isReadableProperty(field)
                                || !rule.test(bean.getPropertyValue(field)),
                        ErrorCode.PARAMS_ERROR, field + "不合法")
        );
    }
}

