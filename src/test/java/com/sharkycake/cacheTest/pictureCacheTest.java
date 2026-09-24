package com.sharkycake.cacheTest;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.sharkycake.picture.entity.Picture;
import com.sharkycake.picture.service.PictureService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;

import javax.annotation.Resource;
import java.util.concurrent.TimeUnit;

@SpringBootTest
public class pictureCacheTest {

    @Resource
    private PictureService pictureService;

    @Resource
    private RedisTemplate<String,Object> redisTemplate;

    @Test
    void testMysql(){
        long start = System.nanoTime();
        Picture picture = pictureService.getById(1);
        long end = System.nanoTime();
        System.out.println("MySQL查询耗时: " + (end - start) / 1_000_000.0 + " ms");
        System.out.println(picture);
    }

    @Test
    void testRedis(){
        // 先在redis中存储对应的数据
        Picture picture = pictureService.getById(1);
        redisTemplate.opsForValue().set("Testpicture", picture,10, TimeUnit.MINUTES);
        // 测试查询
        // 开始时间
        long start = System.nanoTime();
        Picture redisPic = (Picture)redisTemplate.opsForValue().get("Testpicture");
        long end = System.nanoTime();
        System.out.println("Redis查询耗时: " + (end - start) / 1_000_000.0 + " ms");
        System.out.println(redisPic);
    }

    @Test
    void testCaffeine(){
        // 构建本地缓存
        Cache<String,Object> localCache = Caffeine.newBuilder()
                .maximumSize(100)
                .expireAfterWrite(5, TimeUnit.MINUTES)
                .build();
        // 先查数据库并放入Caffeine
        Picture picture = pictureService.getById(1);
        localCache.put("Testpicture",picture);

        // 测试查询
        long start = System.nanoTime();
        Picture caffeinePic = (Picture) localCache.getIfPresent("Testpicture");
        long end = System.nanoTime();
        System.out.println("Caffeine查询耗时: " + (end - start) / 1_000_000.0 + " ms");
        System.out.println(caffeinePic);
    }

}
