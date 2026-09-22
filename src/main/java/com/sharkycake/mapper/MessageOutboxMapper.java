package com.sharkycake.mapper;

import com.sharkycake.model.entity.MessageOutbox;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;

/**
* @author shark
* @description 针对表【message_outbox(Kafka 消息待发送表)】的数据库操作Mapper
* @createDate 2026-09-15 14:56:54
* @Entity com.sharkycake.model.entity.MessageOutbox
*/
public interface MessageOutboxMapper extends BaseMapper<MessageOutbox> {

}




