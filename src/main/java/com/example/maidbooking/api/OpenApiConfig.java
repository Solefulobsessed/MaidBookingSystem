package com.example.maidbooking.api;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Swagger UI at {@code /swagger-ui.html}, the spec at {@code /v3/api-docs}. */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI maidBookingOpenApi() {
        return new OpenAPI().info(new Info()
                .title("Maid booking API")
                .version("v1")
                .description("Book cleaning, cooking and other home services by slot, instantly or on a schedule. "
                        + "The platform assigns the maid. Payments need an Idempotency-Key header. "
                        + "Admin endpoints run the background jobs on demand for demos."));
    }
}
