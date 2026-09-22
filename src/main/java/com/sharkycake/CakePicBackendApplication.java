package com.sharkycake;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableAsync
@SpringBootApplication
//@MapperScan("com.sharkycake.mapper")
@EnableAspectJAutoProxy(exposeProxy = true)
@EnableScheduling()
public class CakePicBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(CakePicBackendApplication.class, args);
    }

}
