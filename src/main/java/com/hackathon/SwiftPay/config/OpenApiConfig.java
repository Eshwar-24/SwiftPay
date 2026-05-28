package com.hackathon.SwiftPay.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI swiftpayOpenAPI() {
        return new OpenAPI()
            .servers(List.of(
                new Server().url("http://localhost:8080").description("Development Server"),
                new Server().url("http://api.swiftpay.local").description("Production Server")
            ))
            .info(new Info()
                .title("SwiftPay API")
                .description("Real-time Payment Ledger Platform - P2P Money Transfer System")
                .version("1.0.0")
                .contact(new Contact()
                    .name("SwiftPay Team")
                    .email("support@swiftpay.com")
                )
            );
    }
}

