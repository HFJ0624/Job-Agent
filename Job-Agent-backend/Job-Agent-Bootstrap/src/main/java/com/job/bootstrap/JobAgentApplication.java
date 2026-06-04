package com.job.bootstrap;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 作者:hfj
 * 功能:Job-Agent 后端项目启动入口
 * 日期:2026/6/2 10:45
 */
@SpringBootApplication(scanBasePackages = "com.job")
public class JobAgentApplication {

    public static void main(String[] args) {
        SpringApplication.run(JobAgentApplication.class, args);
    }
}
