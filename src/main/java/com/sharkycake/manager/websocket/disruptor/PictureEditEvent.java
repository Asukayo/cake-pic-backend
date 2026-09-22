package com.sharkycake.manager.websocket.disruptor;

import com.sharkycake.manager.websocket.model.PictureEditRequestMessage;
import com.sharkycake.model.entity.User;
import lombok.Data;
import org.springframework.web.socket.WebSocketSession;


/**
 * 定于存放在Disruptor队列中的事件
 */
@Data
public class PictureEditEvent {

    /**
     * 消息
     */
    private PictureEditRequestMessage pictureEditRequestMessage;

    /**
     * 当前用户的 session
     */
    private WebSocketSession session;

    /**
     * 当前用户
     */
    private User user;

    /**
     * 图片 id
     */
    private Long pictureId;

}

