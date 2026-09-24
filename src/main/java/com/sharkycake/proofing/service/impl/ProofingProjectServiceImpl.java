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
import com.sharkycake.proofing.dto.ProofingProjectQueryRequest;
import com.sharkycake.proofing.dto.ProofingProjectUpdateRequest;
import com.sharkycake.proofing.entity.ProofingProject;
import com.sharkycake.proofing.enums.ProjectStatus;
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

    public ProofingProjectServiceImpl(UserService userService,
                                      ProofingProjectAuthService proofingProjectAuthService) {
        this.userService = userService;
        this.proofingProjectAuthService = proofingProjectAuthService;
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

    @Override
    public ProofingProjectVO getProject(Long projectId, HttpServletRequest httpServletRequest) {
        requireProjectId(projectId);
        User loginUser = userService.getLoginUser(httpServletRequest);
        ProofingProject project = proofingProjectAuthService.requireProjectPermission(
                projectId, loginUser, SpaceUserPermissionConstant.PROOFING_VIEW);
        return toVo(project);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ProofingProjectVO updateDraft(Long projectId, ProofingProjectUpdateRequest updateRequest,
                                          HttpServletRequest httpServletRequest) {
        requireProjectId(projectId);
        validateDto(updateRequest, UPDATE_RULES);
        ThrowUtils.throwIf(updateRequest.getTitle() == null && updateRequest.getSelectionLimit() == null,
                ErrorCode.PARAMS_ERROR, "至少填写一项修改内容");
        User loginUser = userService.getLoginUser(httpServletRequest);
        ProofingProject project = lockProject(projectId);
        proofingProjectAuthService.requireSpacePermission(
                project.getSpaceId(), loginUser, SpaceUserPermissionConstant.PROOFING_MANAGE);
        requireVersion(project, updateRequest.getExpectedVersion());
        ThrowUtils.throwIf(!ProjectStatus.DRAFT.name().equals(project.getStatus()),
                new BusinessException(40902, "只有草稿可以修改"));

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

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ProofingProjectVO closeProject(Long projectId, ProofingProjectCloseRequest closeRequest,
                                           HttpServletRequest httpServletRequest) {
        requireProjectId(projectId);
        validateDto(closeRequest, CLOSE_RULES);
        User loginUser = userService.getLoginUser(httpServletRequest);
        ProofingProject project = lockProject(projectId);
        proofingProjectAuthService.requireSpacePermission(
                project.getSpaceId(), loginUser, SpaceUserPermissionConstant.PROOFING_CLOSE);
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



