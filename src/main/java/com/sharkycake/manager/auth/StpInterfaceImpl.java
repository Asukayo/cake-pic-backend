package com.sharkycake.manager.auth;

import cn.dev33.satoken.stp.StpInterface;
import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.ObjUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.ReflectUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.extra.servlet.ServletUtil;
import cn.hutool.http.ContentType;
import cn.hutool.http.Header;
import cn.hutool.json.JSONUtil;
import com.sharkycake.constant.SpaceUserPermissionConstant;
import com.sharkycake.exception.BusinessException;
import com.sharkycake.exception.ErrorCode;
import com.sharkycake.manager.auth.model.SpaceUserAuthContext;
import com.sharkycake.model.entity.Picture;
import com.sharkycake.model.entity.Space;
import com.sharkycake.model.entity.SpaceUser;
import com.sharkycake.model.entity.User;
import com.sharkycake.model.enums.SpaceRoleEnum;
import com.sharkycake.model.enums.SpaceTypeEnum;
import com.sharkycake.service.PictureService;
import com.sharkycake.service.SpaceService;
import com.sharkycake.service.SpaceUserService;
import com.sharkycake.service.UserService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.*;

import static com.sharkycake.constant.UserConstant.USER_LOGIN_STATE;

/**
 * 自定义权限加载接口实现类
 */
@Component // 保证该类被SpringBoot扫描，完成Sa-Token的自定义权限权证拓展
public class StpInterfaceImpl implements StpInterface {

    @Value("${server.servlet.context-path}")
    private String contextPath;

    @Resource
    private SpaceUserAuthManager spaceUserAuthManager;

    @Resource
    private SpaceUserService spaceUserService;

    @Resource
    private PictureService pictureService;

    @Resource
    private UserService userService;

    @Resource
    private SpaceService spaceService;

    /**
     * 返回一个账号所拥有的权限码集合
     * @param loginId   用户id
     * @param loginType
     * @return
     */
    @Override
    public List<String> getPermissionList(Object loginId, String loginType) {
        // 判断loginType，只对类型为"space"进行权限校验
        if (!StpKit.SPACE_TYPE.equals(loginType)){
            return new ArrayList<>();
        }
        // 管理员权限表示校验通过
        List<String> ADMIN_PERMISSIONS
                = spaceUserAuthManager.getPermissionsByRole(SpaceRoleEnum.ADMIN.getValue());
        // 获取上下文对象
        SpaceUserAuthContext authContext = getAuthContextByRequest();
        // 如果所有字段都为空，表明查询的是公共图库，可以通过
        if (isAllFieldsNull(authContext)){
            return ADMIN_PERMISSIONS;
        }
        // 获取UserId
        User loginUser
                = (User) StpKit.SPACE.getSessionByLoginId(loginId).get(USER_LOGIN_STATE);
        if (loginUser == null){
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR,"用户未登录");
        }
        Long userId = loginUser.getId();
        // 请求只用于定位资源，成员角色必须从数据库查询。
        // 如果有spaceUserId，必为团队空间，通过数据库查询SpaceUser对象
        Long spaceUserId = authContext.getSpaceUserId();
        if (spaceUserId != null){
            SpaceUser spaceUser = spaceUserService.getById(spaceUserId);
            if (spaceUser == null){
                throw new BusinessException(ErrorCode.NOT_FOUND_ERROR,"未找到用户空间信息");
            }
            // 取出当前登录用户对应的spaceUser
            SpaceUser loginSpaceUser = spaceUserService.lambdaQuery()
                    .eq(SpaceUser::getSpaceId, spaceUser.getSpaceId())
                    .eq(SpaceUser::getUserId, userId)
                    .one();
            if (loginSpaceUser == null){
                return new ArrayList<>();
            }
            // 这里会导致管理员在私有空间没有权限，可以再查一次库处理
            return spaceUserAuthManager.getPermissionsByRole(loginSpaceUser.getSpaceRole());
        }
        // 如果没有spaceUserId，尝试通过SpaceId或者pictureId获取Space对象并处理
        Long spaceId = authContext.getSpaceId();
        Long pictureId = authContext.getPictureId();
        if (pictureId != null){
            // 单张图片操作以数据库中的归属为准，不能被请求中的 spaceId 覆盖。
            // 查询数据库获取对应的图片id，所属空间id，图片创建者id
            Picture picture = pictureService.lambdaQuery()
                    .eq(Picture::getId, pictureId)
                    .select(Picture::getId, Picture::getSpaceId, Picture::getUserId)
                    .one();
            if (picture == null){
                throw new BusinessException(ErrorCode.NOT_FOUND_ERROR,"未找到图片信息");
            }
            spaceId = picture.getSpaceId();
            // 图片在公共图库，仅本人和管理员可以操作
            if (spaceId == null){
                if(picture.getUserId().equals(userId) || userService.isAdmin(loginUser)){
                    return ADMIN_PERMISSIONS;
                }else {
                    // 不是自己的图片，只能进行查看
                    return Collections.singletonList(SpaceUserPermissionConstant.PICTURE_VIEW);
                }
            }
        }
        if (spaceId == null) {
            return ADMIN_PERMISSIONS;
        }
        // 获取Space对象
        Space space = spaceService.getById(spaceId);
        if (space == null){
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR,"未找到空间信息");
        }
        return spaceUserAuthManager.getPermissionList(space, loginUser);
    }

    @Override
    public List<String> getRoleList(Object o, String s) {
        return List.of();
    }



    /**
     * 从请求中获取上下文对象
     */
    private SpaceUserAuthContext getAuthContextByRequest() {
        HttpServletRequest request = ((ServletRequestAttributes) RequestContextHolder.currentRequestAttributes()).getRequest();
        String contentType = request.getHeader(Header.CONTENT_TYPE.getValue());
        SpaceUserAuthContext authRequest;
        // 兼容 get 和 post 操作
        if (contentType != null && ContentType.JSON.getValue()
                .equalsIgnoreCase(StrUtil.subBefore(contentType, ";", false).trim())) {
            String body = ServletUtil.getBody(request);
            authRequest = JSONUtil.toBean(body, SpaceUserAuthContext.class);
        } else {
            Map<String, String> paramMap = ServletUtil.getParamMap(request);
            authRequest = BeanUtil.toBean(paramMap, SpaceUserAuthContext.class);
        }
        // 根据请求路径区分 id 字段的含义
        Long id = authRequest.getId();
        if (ObjUtil.isNotNull(id)) {
            String requestUri = request.getRequestURI();
            String partUri = requestUri.replace(contextPath + "/", "");
            String moduleName = StrUtil.subBefore(partUri, "/", false);
            switch (moduleName) {
                case "pic":
                    authRequest.setPictureId(id);
                    break;
                case "spaceUser":
                    authRequest.setSpaceUserId(id);
                    break;
                case "space":
                    authRequest.setSpaceId(id);
                    break;
                default:
            }
        }
        return authRequest;
    }

    /**
     * 通过反射获取对象的所有字段，进行判空
     * @param object
     * @return
     */
    private boolean isAllFieldsNull(Object object) {
        if (object == null) {
            return true; // 对象本身为空
        }
        // 获取所有字段并判断是否所有字段都为空
        return Arrays.stream(ReflectUtil.getFields(object.getClass()))
                // 获取字段值
                .map(field -> ReflectUtil.getFieldValue(object, field))
                // 检查是否所有字段都为空
                .allMatch(ObjectUtil::isEmpty);
    }


}
