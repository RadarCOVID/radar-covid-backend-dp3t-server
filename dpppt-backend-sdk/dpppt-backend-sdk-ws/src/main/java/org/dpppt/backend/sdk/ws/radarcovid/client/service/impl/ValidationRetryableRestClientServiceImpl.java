/*
 * Copyright (c) 2020 Gobierno de España
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * SPDX-License-Identifier: MPL-2.0
 */

package org.dpppt.backend.sdk.ws.radarcovid.client.service.impl;

import org.dpppt.backend.sdk.ws.radarcovid.client.service.ValidationRestClientService;
import org.dpppt.backend.sdk.ws.radarcovid.client.service.model.Tan;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;

public class ValidationRetryableRestClientServiceImpl implements ValidationRestClientService {

    private static final Logger logger = LoggerFactory.getLogger(ValidationRetryableRestClientServiceImpl.class);

    @Value("${application.endpoint.validation.url}")
    private String validationUrl;

    private final RestTemplate restTemplate;

    public ValidationRetryableRestClientServiceImpl(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    @Override
    @Retryable(maxAttemptsExpression = "#{${application.endpoints.validation.retry.max-attempts:1}}",
            backoff = @Backoff(delayExpression = "#{${application.endpoints.validation.retry.delay:100}}"))
    public boolean validate(String tanString) {
        logger.debug("Entering validationRetryableRestClientServiceImpl.validate('{}')", tanString);
        Tan tan = new Tan();
        tan.setTan(tanString);

        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(Arrays.asList(MediaType.APPLICATION_JSON));
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity httpEntity = new HttpEntity(tan, headers);
        boolean result = false;
        try {
            ResponseEntity<Void> response = restTemplate.postForEntity(validationUrl, httpEntity, Void.class);
            result = (response != null && response.getStatusCode().is2xxSuccessful());
            logger.debug("Validating {} with result {}", tanString, result);
        } catch (HttpClientErrorException ex) {
            if (ex.getStatusCode() != HttpStatus.NOT_FOUND) {
                logger.warn("Exception invoking verification ({}): {}", ex.getStatusCode(), ex.getMessage(), ex);
                throw ex;
            }
        }
        logger.debug("Leaving validationRetryableRestClientServiceImpl with: {}", result);
        return result;
    }
}
