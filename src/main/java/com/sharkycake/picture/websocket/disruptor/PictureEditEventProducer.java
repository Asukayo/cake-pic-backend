package com.sharkycake.picture.websocket.disruptor;

import com.lmax.disruptor.RingBuffer;
import com.lmax.disruptor.dsl.Disruptor;
import com.sharkycake.picture.websocket.model.PictureEditRequestMessage;
import com.sharkycake.user.entity.User;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

import javax.annotation.PreDestroy;
import javax.annotation.Resource;

/**
 * 定义事件生产者
 * 生产者负责将数据发送到Disruptor环形缓冲区中。
 * 为了保证在停机时所有消息都能处理，通过使用shutdown方法实现优雅停机
 */
@Component
@Slf4j
public class PictureEditEventProducer {

    @Resource
    Disruptor<PictureEditEvent> pictureEditEventDisruptor;

    public void publishEvent(PictureEditRequestMessage pictureEditRequestMessage
            , WebSocketSession session, User user, Long pictureId) {
        RingBuffer<PictureEditEvent> ringBuffer = pictureEditEventDisruptor.getRingBuffer();
        // 获取可以生成的位置
        long next = ringBuffer.next();
        PictureEditEvent pictureEditEvent = ringBuffer.get(next);
        pictureEditEvent.setSession(session);
        pictureEditEvent.setPictureEditRequestMessage(pictureEditRequestMessage);
        pictureEditEvent.setUser(user);
        pictureEditEvent.setPictureId(pictureId);
        // 发布事件
        ringBuffer.publish(next);
    }

    /**
     * 优雅停机
     */
    @PreDestroy
    public void close() {
        pictureEditEventDisruptor.shutdown();
    }
}

