package com.sharkycake.proofing.auth;

import com.sharkycake.common.exception.ErrorCode;
import com.sharkycake.common.exception.ThrowUtils;
import com.sharkycake.proofing.entity.ProofingProject;
import com.sharkycake.proofing.mapper.ProofingProjectMapper;
import com.sharkycake.space.auth.SpaceUserAuthManager;
import com.sharkycake.space.entity.Space;
import com.sharkycake.space.enums.SpaceTypeEnum;
import com.sharkycake.space.service.SpaceService;
import com.sharkycake.user.entity.User;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * 根据选片单所属空间检查当前用户权限。
 */
@Component
public class ProofingProjectAuthService {

    private final ProofingProjectMapper proofingProjectMapper;
    private final SpaceService spaceService;
    private final SpaceUserAuthManager spaceUserAuthManager;

    ProofingProjectAuthService(
            SpaceService spaceService,
            SpaceUserAuthManager spaceUserAuthManager,
            ProofingProjectMapper proofingProjectMapper) {
        this.spaceService = spaceService;
        this.spaceUserAuthManager = spaceUserAuthManager;
        this.proofingProjectMapper = proofingProjectMapper;
    }

    // 按项目 ID 检查团队权限
    public ProofingProject requireProjectPermission(
            Long projectId, User loginUser, String permission) {
        ThrowUtils.throwIf(
                projectId == null || loginUser == null || permission == null,
                ErrorCode.PARAMS_ERROR, "参数校验不合法"
        );
        // 用 projectId 从数据库读取 ProofingProject。
        ProofingProject proofingProject = proofingProjectMapper.selectById(projectId);
        ThrowUtils.throwIf(
                proofingProject == null,
                ErrorCode.NOT_FOUND_ERROR, "对应选单项目不存在"
        );
        requireSpacePermission(proofingProject.getSpaceId(), loginUser, permission);
        return proofingProject;

    }

    /**
     * 根据SpaceId查询权限
     */
    public void requireSpacePermission(Long spaceId, User loginUser, String permission) {
        //用项目记录里的 spaceId 读取 Space，确认它是 TEAM或者PRIVATE项目也可以。
        Space space = spaceService.getById(spaceId);
        ThrowUtils.throwIf(
                space == null,
                ErrorCode.NO_AUTH_ERROR, "公共空间不能创建选单"
        );
        SpaceTypeEnum type = SpaceTypeEnum.getEnumByValue(space.getSpaceType());
        ThrowUtils.throwIf(type == null, ErrorCode.NO_AUTH_ERROR, "不支持的空间类型");
        if (type == SpaceTypeEnum.PRIVATE) {
            // 私人空间判断是否是空间拥有者创建
            ThrowUtils.throwIf(
                    !Objects.equals(space.getUserId(), loginUser.getId()),
                    ErrorCode.NO_AUTH_ERROR, "无操作权限");
        } else {
            // 按传入权限检查成员
            ThrowUtils.throwIf(
                    !spaceUserAuthManager.getPermissionList(space, loginUser).contains(permission),
                    ErrorCode.NO_AUTH_ERROR, "无操作权限");
        }
    }
}
