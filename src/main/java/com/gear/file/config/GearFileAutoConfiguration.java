package com.gear.file.config;

import com.gear.file.core.FileEngine;
import com.gear.file.strategy.FileParser;
import jakarta.validation.Validator;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;

import java.util.List;

@AutoConfiguration
@ComponentScan(basePackages = "com.gear.file")
public class GearFileAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public FileEngine fileEngine(List<FileParser> parsers, Validator validator) {
        // 将 Spring Boot 官方配置好的高并发 Validator 注入进来
        return new FileEngine(parsers, validator);
    }
}