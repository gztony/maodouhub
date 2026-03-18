package com.zhengmeng.hub;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * MaoDouHub 服务入口
 * <p>
 * 毛豆消息中转服务 —— 大龙虾核心组件。
 * 负责小毛豆（手机）与毛豆（PC）之间的消息路由、文件中转和认证管理。
 */
@SpringBootApplication
@EnableScheduling
public class HubApplication {
    public static void main(String[] args) {
        SpringApplication.run(HubApplication.class, args);
    }
}
