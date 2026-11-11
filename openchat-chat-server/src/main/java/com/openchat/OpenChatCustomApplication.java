package com.openchat;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

@EnableDiscoveryClient
@SpringBootApplication(scanBasePackages = {"com.openchat"})
@MapperScan({"com.openchat.**.mapper"})
@EnableAspectJAutoProxy(exposeProxy = true)
public class OpenChatCustomApplication {

    static void main(String[] args) {
        SpringApplication.run(OpenChatCustomApplication.class,args);
    }

}