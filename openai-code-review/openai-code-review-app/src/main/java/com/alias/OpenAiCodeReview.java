package com.alias;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Configurable;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@Configurable
@MapperScan("com.alias.infrastructure.mapper")
public class OpenAiCodeReview {

    public static void main(String[] args) {
        SpringApplication.run(OpenAiCodeReview.class);
    }

}
