package com.sharkycake;


import cn.hutool.core.util.StrUtil;
import com.sharkycake.config.CosConfig;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import javax.annotation.Resource;

@SpringBootTest
public class CosTest {

    @Resource
    private CosConfig cosConfig;
    /**
     * 获取CosClient的Host信息，用于获得key值（）
     */
    @Test
    void getCosClientHost(){
//        System.out.println(cosConfig.getHost());
//        String fullKey = "https://cake-pic-1316699316.cos.ap-shanghai.myqcloud.com//public/1996871588537298945/2026-01-31_HlGMK7hOCau7jsyj.png@!te5";
//        String keyWOHost = StrUtil.removePrefix(fullKey, cosConfig.getHost());
//        System.out.println(keyWOHost);
//        System.out.println(StrUtil.removePrefix(keyWOHost, "/"));

        String fullKey = "https://cake-pic-1316699316.cos.ap-shanghai.myqcloud.com//public/1996871588537298945/2026-01-31_HlGMK7hOCau7jsyj.png@!te5";
        String host = cosConfig.getHost();
        String cleanPath = fullKey.replace(host, "").replaceAll("^/+", "");
        System.out.println(cleanPath);
    }

}
