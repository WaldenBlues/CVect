package com.walden.cvect.logging.config;

import com.walden.cvect.logging.mdc.MdcTaskDecorator;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskDecorator;

@Configuration
@EnableConfigurationProperties(LogProperties.class)
public class LoggingConfig {

    @Bean
    public TaskDecorator mdcTaskDecorator() {
        return new MdcTaskDecorator();
    }
}
