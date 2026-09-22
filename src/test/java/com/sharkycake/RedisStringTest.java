package com.sharkycake;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.Objects;

@SpringBootTest
public class RedisStringTest {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Test
    public void testRedisStringOperations() {
        // 获取操作对象
        ValueOperations<String, String> ops = stringRedisTemplate.opsForValue();

        // key和value
        String key = "testKey";
        String value = "testValue";

        // 1.测试新增或者更新操作
        ops.set(key, value);
        String getKey = ops.get(key);
        assert Objects.equals(getKey, value);

        // 2.测试修改操作
        String changedValue = "changedValue";
        ops.set(key, changedValue);
        assert Objects.equals(changedValue, ops.get(key));

        // 3.测试删除操作
        stringRedisTemplate.delete(key);
        String deleted = ops.get(key);
        assert deleted == null;

    }



}
