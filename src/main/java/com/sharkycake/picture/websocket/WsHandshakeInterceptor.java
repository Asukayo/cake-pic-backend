package com.sharkycake.picture.websocket;

import cn.hutool.core.util.ObjUtil;
import com.github.xiaoymin.knife4j.core.util.StrUtil;
import com.sharkycake.space.constant.SpaceUserPermissionConstant;
import com.sharkycake.space.auth.SpaceUserAuthManager;
import com.sharkycake.picture.entity.Picture;
import com.sharkycake.space.entity.Space;
import com.sharkycake.user.entity.User;
import com.sharkycake.space.enums.SpaceTypeEnum;
import com.sharkycake.picture.service.PictureService;
import com.sharkycake.space.service.SpaceService;
import com.sharkycake.user.service.UserService;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * webSocket拦截器
 */
@Component
@Slf4j
public class WsHandshakeInterceptor implements HandshakeInterceptor {

    @Resource
    private UserService userService;

    @Resource
    private PictureService pictureService;

    @Resource
    private SpaceService spaceService;

    @Resource
    private SpaceUserAuthManager spaceUserAuthManager;

    /**
     * 在websocket连接前先进行权限校验，如果用户没有团队内编辑图片的权力，则直接拒绝握手
     *
     * @param request
     * @param response
     * @param wsHandler
     * @param attributes
     * @return
     * @throws Exception
     */

    @Override
    public boolean beforeHandshake(@NonNull ServerHttpRequest request,
                                   @NonNull ServerHttpResponse response,
                                   @NonNull WebSocketHandler wsHandler,
                                   @NonNull Map<String, Object> attributes) throws Exception {
        if (request instanceof ServletServerHttpRequest) {

            ServletServerHttpRequest temp = (ServletServerHttpRequest) request;
            HttpServletRequest servletRequest = temp.getServletRequest();
            // 获取请求参数
            String pictureId = servletRequest.getParameter("pictureId");
            if (StrUtil.isBlank(pictureId)) {
                log.error("缺少图片参数，拒绝握手");
                return false;
            }
            User loginUser = userService.getLoginUser(servletRequest);
            if (ObjUtil.isEmpty(loginUser)) {
                log.error("用户未登录，拒绝握手");
                return false;
            }
            // 校验用户是否具有该图片权限
            Picture picture = pictureService.getById(pictureId);
            if (picture == null) {
                log.error("图片不存在，拒绝握手");
                return false;
            }
            Long spaceId = picture.getSpaceId();
            Space space = null;
            if (spaceId != null) {
                space = spaceService.getById(spaceId);
                if (space == null) {
                    log.error("空间不存在，拒绝握手");
                    return false;
                }
                if(space.getSpaceType() != SpaceTypeEnum.TEAM.getValue()){
                    log.error("不是团队空间拒绝握手");
                    return false;
                }
            }
            List<String> permissionList
                    = spaceUserAuthManager.getPermissionList(space, loginUser);
            if(!permissionList.contains(SpaceUserPermissionConstant.PICTURE_EDIT)){
                log.error("没有图片编辑权限,拒绝握手");
                return false;
            }
            // 设置attributes
            attributes.put("pictureId", Long.valueOf(pictureId));
            attributes.put("user", loginUser);
            attributes.put("userId", loginUser.getId());
        }
        return true;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response, WebSocketHandler wsHandler, Exception exception) {

    }
}

