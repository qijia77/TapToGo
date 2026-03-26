package com.jia.taptogo;

import com.jia.taptogo.config.AmapProperties;
import com.jia.taptogo.config.OpenAiProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
@EnableConfigurationProperties({OpenAiProperties.class, AmapProperties.class})
public class TapToGoApplication {

    private static final Logger log = LoggerFactory.getLogger(TapToGoApplication.class);

    public static void main(String[] args) {
        SpringApplication.run(TapToGoApplication.class, args);
    }

    @Bean
    ApplicationRunner startupModeReporter(OpenAiProperties openAiProperties, AmapProperties amapProperties) {
        return args -> {
            String planningMode = openAiProperties.configured()
                    ? (openAiProperties.webSearchEnabled() ? "openai-web-search" : "openai")
                    : "demo";

            log.info("TapToGo planner mode: {}", planningMode);
            log.info("OpenAI configured: {}, model: {}, baseUrl: {}",
                    openAiProperties.configured(),
                    openAiProperties.model(),
                    openAiProperties.baseUrl());
            log.info("OpenAI web search enabled: {}", openAiProperties.webSearchEnabled());
            log.info("Amap configured: {}", amapProperties.configured());
        };
    }
}
