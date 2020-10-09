/*
 * Copyright (c) 2020 Gobierno de España
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * SPDX-License-Identifier: MPL-2.0
 */

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
