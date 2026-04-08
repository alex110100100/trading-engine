package com.alex.trading_engine.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI tradingEngineOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Trading Engine API")
                        .description("REST API for submitting orders, viewing trades and per-symbol order books."
                                + "/nMoney fields are serialized as JSON numbers from `BigDecimal`.")
                        .version("1.0"));
    }
}
