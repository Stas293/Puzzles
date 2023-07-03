package com.projects.puzzles.config;

import com.projects.puzzles.utility.PuzzleConfig;
import lombok.extern.slf4j.Slf4j;
import org.apache.coyote.ProtocolHandler;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.embedded.tomcat.TomcatProtocolHandlerCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.Executors;

@Configuration
@Slf4j
@ConfigurationPropertiesScan("com.projects.puzzles.utility")
@EnableConfigurationProperties(PuzzleConfig.class)
public class ApplicationConfiguration {
    @Bean
    TomcatProtocolHandlerCustomizer<ProtocolHandler> protocolHandlerVirtualThreadsExecutorCustomizer() {
        return protocolHandler -> {
            log.info("Configuring Tomcat protocol handler to use virtual threads");
            protocolHandler.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
        };
    }
}
