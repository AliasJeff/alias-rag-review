package com.alias.config;

import com.alias.infrastructure.typehandler.JsonbTypeHandler;
import com.alias.infrastructure.typehandler.UUIDTypeHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;

@Configuration
public class MyBatisConfig {

    @Bean
    public org.mybatis.spring.SqlSessionFactoryBean sqlSessionFactory(DataSource dataSource) {
        org.mybatis.spring.SqlSessionFactoryBean factoryBean = new org.mybatis.spring.SqlSessionFactoryBean();
        factoryBean.setDataSource(dataSource);
        // 注册 UUID 和 JSONB 类型处理器
        factoryBean.setTypeHandlers(new UUIDTypeHandler(), new JsonbTypeHandler());
        return factoryBean;
    }
}