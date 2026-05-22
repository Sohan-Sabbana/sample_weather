package com.example.weather.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI weatherOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Weather API")
                        .description("Sample weather service used as the deployable artifact "
                                + "for the Jenkins CD log-analyzer pipeline. "
                                + "Every request emits structured JSON logs carrying X-Trace-Id "
                                + "so logs can be correlated across build, deploy, MAT and regression stages "
                                + "once shipped to Elasticsearch.")
                        .version("1.0.0")
                        .contact(new Contact().name("Platform Team").email("platform@example.com"))
                        .license(new License().name("Apache 2.0")));
    }
}
