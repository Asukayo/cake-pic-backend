package com.sharkycake.manager.websocket;

import cn.hutool.core.collection.CollUtil;

import cn.hutool.json.JSONUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.sharkycake.manager.websocket.disruptor.PictureEditEventProducer;
import com.sharkycake.manager.websocket.model.PictureEditMessageTypeEnum;
import com.sharkycake.manager.websocket.model.PictureEditRequestMessage;
import com.sharkycake.manager.websocket.model.PictureEditResponseMessage;
import com.sharkycake.model.entity.User;
import com.sharkycake.service.UserService;
import lombok.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import com.sharkycake.manager.websocket.model.PictureEditActionEnum;
import javax.annotation.Resource;
import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;


/**
 * websocket处理器
 * 在连接成功\连接关闭\接收到客户端消息时进行相应的处理
 */
@Component
// 实现TextWebSocketHandler接口,这样就能以字符串的方式发送和接受消息了
public class PictureEditHandler extends TextWebSocketHandler {

    @Resource
    private UserService userService;

    // 每张图片的编辑状态，key: pictureId, value: 当前正在编辑的用户 ID
    private final Map<Long, Long> pictureEditingUsers = new ConcurrentHashMap<>();

    // 保存所有连接的会话，key: pictureId, value: 用户会话集合
    private final Map<Long, Set<WebSocketSession>> pictureSessions = new ConcurrentHashMap<>();

    //
    @Resource
    private PictureEditEventProducer pictureEditEventProducer;


    /**
     * 广播消息的方法,根据PictureId,将响应消息发给编辑该图片的所有绘画
     * 考虑到可能有消息不需要发送给编辑者本人的情况,该方法还可以接受excludeSession参数
     * 支持排除掉向某个会话发送消息
     * TIPS:由于前端JS的长整数可能会丢失精度,所以使用Jackson自定义序列化器,
     * 在将对象转换为JSON字符串时,将Long类型转化为String类型
     */
    private void broadcastToPicture(Long pictureId,
                                    PictureEditResponseMessage pictureEditResponseMessage,
                                    WebSocketSession excludeSession) throws IOException {
        // 获取当前图片的所有会话集合
        Set<WebSocketSession> sessionSet = pictureSessions.get(pictureId);
        if(CollUtil.isNotEmpty(sessionSet)) {
            // 创建objectMapper
            ObjectMapper objectMapper = new ObjectMapper();
            // 配置序列化:将Long类型转为String,解决精度丢失问题
            SimpleModule module = new SimpleModule();
            module.addSerializer(Long.class, ToStringSerializer.instance);
            // 同时支持long基本类型
            module.addSerializer(Long.TYPE, ToStringSerializer.instance);
            objectMapper.registerModule(module);
            // 序列化为JSON字符串
            String message = objectMapper.writeValueAsString(pictureEditResponseMessage);
            TextMessage textMessage = new TextMessage(message);
            for (WebSocketSession session : sessionSet) {
                // 排除掉的session不发送
                if (excludeSession != null && excludeSession.equals(session)) {
                    continue;
                }
                if (session.isOpen()) {
                    session.sendMessage(textMessage);
                }
            }
        }
    }

    // 全部广播
    private void broadcastToPicture(Long pictureId, PictureEditResponseMessage pictureEditResponseMessage) throws Exception {
        broadcastToPicture(pictureId, pictureEditResponseMessage, null);
    }

    /**
     * 实现会话连接建立成功后执行的方法,保存会话到集合中,并给其他会话发送消息
     * @param session
     * @throws Exception
     */
    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        // 保存会话到集合中
        User user = (User) session.getAttributes().get("user");
        Long pictureId = (Long) session.getAttributes().get("pictureId");
        pictureSessions.putIfAbsent(pictureId, ConcurrentHashMap.newKeySet());
        pictureSessions.get(pictureId).add(session);

        // 构造响应
        PictureEditResponseMessage pictureEditResponseMessage = new PictureEditResponseMessage();
        pictureEditResponseMessage.setType(PictureEditMessageTypeEnum.INFO.getValue());
        String message = String.format("%s加入编辑", user.getUserName());
        pictureEditResponseMessage.setMessage(message);
        pictureEditResponseMessage.setUser(userService.convert2UserVO(user));
        // 广播给同一张图片的用户
        broadcastToPicture(pictureId, pictureEditResponseMessage);
    }

    /**
     * 当前用户断开连接时,需要移除当前用户的编辑状态,并从集合中删除当前会话,还可以给其他客户端发送消息通知
     */
    @Override
    public void afterConnectionClosed(WebSocketSession session, @NonNull CloseStatus status) throws Exception {
        // 获取当前要退出的session属性
        Map<String, Object> attributes = session.getAttributes();
        Long pictureId = (Long) attributes.get("pictureId");
        User user = (User) attributes.get("user");
        // 移除当前用户的编辑状态
        handleExitEditMessage(null, session, user, pictureId);

        // 删除会话
        Set<WebSocketSession> sessionSet = pictureSessions.get(pictureId);
        if (sessionSet != null) {
            sessionSet.remove(session);
            if (sessionSet.isEmpty()) {
                pictureSessions.remove(pictureId);
            }
        }

        // 响应
        PictureEditResponseMessage pictureEditResponseMessage = new PictureEditResponseMessage();
        pictureEditResponseMessage.setType(PictureEditMessageTypeEnum.INFO.getValue());
        String message = String.format("%s离开编辑", user.getUserName());
        pictureEditResponseMessage.setMessage(message);
        pictureEditResponseMessage.setUser(userService.convert2UserVO(user));
        broadcastToPicture(pictureId, pictureEditResponseMessage);
    }


//    /**
//     * 接收客户端消息的方法,根据消息类别执行不同的处理
//     */
//    @Override
//    @Deprecated
//    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
//        // 将消息解析为 PictureEditMessage
//        PictureEditRequestMessage pictureEditRequestMessage = JSONUtil.toBean(message.getPayload(), PictureEditRequestMessage.class);
//        // 获取当前操作类型
//        String type = pictureEditRequestMessage.getType();
//        // 获取标准操作常量
//        PictureEditMessageTypeEnum pictureEditMessageTypeEnum = PictureEditMessageTypeEnum.valueOf(type);
//
//        // 从 Session 属性中获取公共参数
//        // 传入的是当前session,所以获取的也是当前session对话中的参数
//        Map<String, Object> attributes = session.getAttributes();
//        User user = (User) attributes.get("user");
//        Long pictureId = (Long) attributes.get("pictureId");
//
//        // 调用对应的消息处理方法
//        switch (pictureEditMessageTypeEnum) {
//            case ENTER_EDIT:
//                handleEnterEditMessage(pictureEditRequestMessage, session, user, pictureId);
//                break;
//            case EDIT_ACTION:
//                handleEditActionMessage(pictureEditRequestMessage, session, user, pictureId);
//                break;
//            case EXIT_EDIT:
//                handleExitEditMessage(pictureEditRequestMessage, session, user, pictureId);
//                break;
//            default:
//                PictureEditResponseMessage pictureEditResponseMessage = new PictureEditResponseMessage();
//                pictureEditResponseMessage.setType(PictureEditMessageTypeEnum.ERROR.getValue());
//                pictureEditResponseMessage.setMessage("消息类型错误");
//                pictureEditResponseMessage.setUser(userService.convert2UserVO(user));
//                session.sendMessage(new TextMessage(JSONUtil.toJsonStr(pictureEditResponseMessage)));
//        }
//    }


    @Override
    protected void handleTextMessage(WebSocketSession session,
                                     TextMessage message) throws Exception {
        // 将消息解析为 PictureEditMessage
        PictureEditRequestMessage pictureEditRequestMessage =
                JSONUtil.toBean(message.getPayload(), PictureEditRequestMessage.class);
        // 从 Session 属性中获取公共参数
        Map<String, Object> attributes = session.getAttributes();
        User user = (User) attributes.get("user");
        Long pictureId = (Long) attributes.get("pictureId");
        // 生产消息
        pictureEditEventProducer.publishEvent(pictureEditRequestMessage, session, user, pictureId);
    }



    /**
     * 用户进入编辑状态的代码
     * @param pictureEditRequestMessage
     * @param session
     * @param user
     * @param pictureId
     * @throws Exception
     */
    public void handleEnterEditMessage(PictureEditRequestMessage pictureEditRequestMessage,
                                       WebSocketSession session, User user, Long pictureId) throws Exception {
        // 没有用户正在编辑该图片，才能进入编辑
        // 在并发安全哈希表中查找是否有用户拥有当前图片的编辑锁
        if (!pictureEditingUsers.containsKey(pictureId)) {
            // 设置当前用户为编辑用户,加锁操作
            pictureEditingUsers.put(pictureId, user.getId());
            // 新建一个图片编辑响应信息
            PictureEditResponseMessage pictureEditResponseMessage = new PictureEditResponseMessage();
            // 将响应信息的类型设置为进入编辑
            pictureEditResponseMessage.setType(PictureEditMessageTypeEnum.ENTER_EDIT.getValue());
            String message = String.format("%s开始编辑图片", user.getUserName());
            pictureEditResponseMessage.setMessage(message);
            pictureEditResponseMessage.setUser(userService.convert2UserVO(user));
            // 将消息广播到所有会话
            broadcastToPicture(pictureId, pictureEditResponseMessage);
        }
    }

    /**
     * 用户执行编辑操作,将该操作同步给除了当前用户之外的所有客户端
     * @param pictureEditRequestMessage
     * @param session
     * @param user
     * @param pictureId
     * @throws Exception
     */
    public void handleEditActionMessage(PictureEditRequestMessage pictureEditRequestMessage,
                                        WebSocketSession session, User user, Long pictureId) throws Exception {
        // 获取当前编辑的用户id和编辑类型
        Long editingUserId = pictureEditingUsers.get(pictureId);
        String editAction = pictureEditRequestMessage.getEditAction();
        PictureEditActionEnum actionEnum = PictureEditActionEnum.getEnumByValue(editAction);
        // 如果没有做任何编辑操作,直接返回
        if (actionEnum == null) {
            return;
        }
        // 确认是当前编辑者
        if (editingUserId != null && editingUserId.equals(user.getId())) {
            PictureEditResponseMessage pictureEditResponseMessage = new PictureEditResponseMessage();
            pictureEditResponseMessage.setType(PictureEditMessageTypeEnum.EDIT_ACTION.getValue());
            String message = String.format("%s执行%s", user.getUserName(), actionEnum.getText());
            pictureEditResponseMessage.setMessage(message);
            pictureEditResponseMessage.setEditAction(editAction);
            pictureEditResponseMessage.setUser(userService.convert2UserVO(user));
            // 广播给除了当前客户端之外的其他用户，否则会造成重复编辑
            broadcastToPicture(pictureId, pictureEditResponseMessage, session);
        }
    }

    /**
     * 用户退出编辑操作时,移除当前用户的编辑状态,并向其他客户端发送消息
     */
    public void handleExitEditMessage(PictureEditRequestMessage pictureEditRequestMessage,
                                      WebSocketSession session, User user, Long pictureId) throws Exception {
        Long editingUserId = pictureEditingUsers.get(pictureId);
        if (editingUserId != null && editingUserId.equals(user.getId())) {
            // 移除当前用户的编辑状态
            pictureEditingUsers.remove(pictureId);
            // 构造响应，发送退出编辑的消息通知
            PictureEditResponseMessage pictureEditResponseMessage = new PictureEditResponseMessage();
            pictureEditResponseMessage.setType(PictureEditMessageTypeEnum.EXIT_EDIT.getValue());
            String message = String.format("%s退出编辑图片", user.getUserName());
            pictureEditResponseMessage.setMessage(message);
            pictureEditResponseMessage.setUser(userService.convert2UserVO(user));
            broadcastToPicture(pictureId, pictureEditResponseMessage);
        }
    }





}

