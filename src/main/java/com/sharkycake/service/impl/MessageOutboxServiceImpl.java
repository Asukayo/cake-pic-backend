package com.sharkycake.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.sharkycake.model.entity.MessageOutbox;
import com.sharkycake.service.MessageOutboxService;
import com.sharkycake.mapper.MessageOutboxMapper;
import org.springframework.stereotype.Service;

/**
* @author shark
* @description 针对表【message_outbox(Kafka 消息待发送表)】的数据库操作Service实现
* @createDate 2026-09-15 14:56:54
*/
@Service
public class MessageOutboxServiceImpl extends ServiceImpl<MessageOutboxMapper, MessageOutbox>
    implements MessageOutboxService{

}




