package com.sharkycake.service;

import com.sharkycake.model.entity.MessageOutbox;
import com.baomidou.mybatisplus.extension.service.IService;

/**
* @author shark
* @description 针对表【message_outbox(Kafka 消息待发送表)】的数据库操作Service
* @createDate 2026-09-15 14:56:54
*/
public interface MessageOutboxService extends IService<MessageOutbox> {

}
