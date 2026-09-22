package com.sharkycake.manager.auth;

import cn.hutool.core.io.resource.ResourceUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.sharkycake.constant.SpaceUserPermissionConstant;
import com.sharkycake.exception.ErrorCode;
import com.sharkycake.exception.ThrowUtils;
import com.sharkycake.manager.auth.model.SpaceUserAuthConfig;
import com.sharkycake.manager.auth.model.SpaceUserRole;
import com.sharkycake.model.entity.Space;
import com.sharkycake.model.entity.SpaceUser;
import com.sharkycake.model.entity.User;
import com.sharkycake.model.enums.SpaceRoleEnum;
import com.sharkycake.model.enums.SpaceTypeEnum;
import com.sharkycake.model.vo.SpacePermissionVO;
import com.sharkycake.service.SpaceUserService;
import com.sharkycake.service.UserService;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 用于加载配置文件到对象，并根据角色获取权限列表的方法
 */
@Component
public class SpaceUserAuthManager {

    @Resource
    private SpaceUserService spaceUserService;

    @Resource
    private UserService userService;

    /**
     * 定义权限配置类常量，包含了角色列表和权限列表
     */
    public static final SpaceUserAuthConfig SPACE_USER_AUTH_CONFIG;

    /**
     * static字段包裹，类被初次加载时初始化
     */
    static {
        String json = ResourceUtil.readUtf8Str("biz/spaceUserAuthConfig.json");
        SPACE_USER_AUTH_CONFIG = JSONUtil.toBean(json, SpaceUserAuthConfig.class);
    }

    /**
     * 根据角色获取权限列表
     */
    public List<String> getPermissionsByRole(String spaceUserRole) {
        if (StrUtil.isBlank(spaceUserRole)) {
            return new ArrayList<>();
        }
        // 找到匹配的角色
        SpaceUserRole role = SPACE_USER_AUTH_CONFIG.getRoles().stream()
                .filter(r -> spaceUserRole.equals(r.getKey()))
                .findFirst()
                .orElse(null);
        if (role == null) {
            return new ArrayList<>();
        }
        return role.getPermissions();
    }

    public List<String> getPermissionList(Space space, User loginUser) {
        return getPermissionsByRole(resolveRole(space, loginUser));
    }

    /**
     * 返回空间级权限快照，单张图片的审核、上传者等限制仍由操作接口检查。
     * 非成员不返回空间信息；平台管理员不自动获得团队成员身份。
     */
    public SpacePermissionVO getPermissionView(Space space, User loginUser) {
        ThrowUtils.throwIf(loginUser == null || loginUser.getId() == null, ErrorCode.NOT_LOGIN_ERROR);
        ThrowUtils.throwIf(space == null, ErrorCode.NOT_FOUND_ERROR, "空间不存在");
        String role = resolveRole(space, loginUser);
        List<String> permissions = getPermissionsByRole(role);
        ThrowUtils.throwIf(permissions.isEmpty(), ErrorCode.NO_AUTH_ERROR, "没有空间访问权限");
        boolean owner = Objects.equals(space.getUserId(), loginUser.getId());
        boolean team = Objects.equals(space.getSpaceType(), SpaceTypeEnum.TEAM.getValue());
        SpacePermissionVO view = new SpacePermissionVO();
        view.setSpaceId(space.getId());
        view.setSpaceType(space.getSpaceType());
        view.setSpaceRole(team ? role : null);
        view.setOwner(owner);
        view.setPermissionList(new ArrayList<>(permissions));
        view.setCanUpload(owner && permissions.contains(SpaceUserPermissionConstant.PICTURE_UPLOAD));
        view.setCanBatchEdit(owner && permissions.contains(SpaceUserPermissionConstant.PICTURE_EDIT));
        view.setCanAnalyze(owner);
        view.setCanSearchByColor(owner);
        view.setCanLeave(team && !owner);
        return view;
    }

    private String resolveRole(Space space, User loginUser) {
        if (loginUser == null) {
            return null;
        }
        if (space == null) {
            return userService.isAdmin(loginUser) ? SpaceRoleEnum.ADMIN.getValue() : null;
        }
        SpaceTypeEnum spaceTypeEnum = SpaceTypeEnum.getEnumByValue(space.getSpaceType());
        if (spaceTypeEnum == null) {
            return null;
        }
        switch (spaceTypeEnum) {
            case PRIVATE:
                return Objects.equals(space.getUserId(), loginUser.getId()) || userService.isAdmin(loginUser)
                        ? SpaceRoleEnum.ADMIN.getValue() : null;
            case TEAM:
                SpaceUser spaceUser = spaceUserService.getOne(new QueryWrapper<SpaceUser>()
                        .eq("spaceId", space.getId()).eq("userId", loginUser.getId()));
                return spaceUser == null ? null : spaceUser.getSpaceRole();
            default:
                return null;
        }
    }

}
