package com.ichimoku.screener.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI ichimokuOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Ichimoku Screener API")
                        .description("Trojpolowka - 3-kolumnowy system analizy Ichimoku dla GPW i S&P 500")
                        .version("1.0.0"));
    }
}
