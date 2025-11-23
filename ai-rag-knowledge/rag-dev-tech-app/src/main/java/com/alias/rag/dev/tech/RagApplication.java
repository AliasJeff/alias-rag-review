package com.alias.rag.dev.tech;

import org.springframework.beans.factory.annotation.Configurable;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@Configurable
@ComponentScan(basePackages = {"com.alias.rag.dev.tech", "com.alias.rag.dev.tech.trigger"})
public class RagApplication {

  public static void main(String[] args) {
    SpringApplication.run(RagApplication.class);
  }
}
