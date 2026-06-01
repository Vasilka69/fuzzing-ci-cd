package ru.diplom.cicd.master.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class OpenSearchConfig {

    @Bean
    public RestClient openSearchRestClient(AppProperties appProperties) {
        return RestClient.builder()
                .baseUrl(appProperties.getOpensearch().getUrl())
                .build();
    }
}
