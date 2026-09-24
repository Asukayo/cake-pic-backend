package com.sharkycake.infrastructure.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import org.springframework.boot.jackson.JsonComponent;
import org.springframework.context.annotation.Bean;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.stereotype.Component;

/**
 * Spring MVC Json 配置
 */
// springboot的json组件注解，自动注册配置
@Component
public class JsonConfig {

    /**
     * 添加 Long 转 json 精度丢失的配置
     */
    @Bean
    public ObjectMapper jacksonObjectMapper(Jackson2ObjectMapperBuilder builder) {
        // 创建ObjectMapper实例（JSON序列化核心类）
        ObjectMapper objectMapper = builder.createXmlMapper(false).build();
        // 创建自定义序列化模块
        SimpleModule module = new SimpleModule();
        // 将Long类型序列化为字符串
        module.addSerializer(Long.class, ToStringSerializer.instance); // 包装类
        module.addSerializer(Long.TYPE, ToStringSerializer.instance);  // 基本类
        // 注册模块到ObjectMapper
        objectMapper.registerModule(module);
        return objectMapper;
    }

}
