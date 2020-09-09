package org.dpppt.backend.sdk.ws.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

     @Value("${application.openapi.title:}")
     private String openApiTitle;

     @Value("${application.openapi.description:}")
     private String openApiDescription;

     @Value("${application.openapi.version:}")
     private String openApiVersion;

     @Value("${application.openapi.terms-of-service:}")
     private String openApiTermsOfService;

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                              .title(openApiTitle)
                              .version(openApiVersion)
                              .description(openApiDescription)
                              .termsOfService(openApiTermsOfService));
    }

}
