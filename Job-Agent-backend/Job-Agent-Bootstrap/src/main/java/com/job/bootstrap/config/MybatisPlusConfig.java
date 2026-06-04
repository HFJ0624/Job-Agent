package com.job.bootstrap.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 作者:hfj
 * 功能:MyBatis-Plus 数据库增强配置
 * 日期:2026/6/2 10:45
 */
@Configuration
@MapperScan("com.job.**.mapper")
public class MybatisPlusConfig {

    /**
     * 配置 MyBatis-Plus 插件。
     *
     * @return 返回包含分页能力的 MyBatis-Plus 拦截器
     */
    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        // 1. 创建 MyBatis-Plus 总拦截器。
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();

        // 2. 添加 MySQL 分页插件，page() 查询才能自动拼接分页 SQL。
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
        return interceptor;
    }
}
