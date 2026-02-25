package com.gear.file.config;

import com.gear.file.core.FileEngine;
import com.gear.file.strategy.FileParser;
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
    public FileEngine fileEngine(List<FileParser> parsers) {
        // 去掉了 resourceLoader
        return new FileEngine(parsers);
    }
}