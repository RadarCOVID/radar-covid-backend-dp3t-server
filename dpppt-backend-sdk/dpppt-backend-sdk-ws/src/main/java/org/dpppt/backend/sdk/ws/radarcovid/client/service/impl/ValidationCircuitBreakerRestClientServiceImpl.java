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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ValidationCircuitBreakerRestClientServiceImpl implements ValidationRestClientService {

    private static final Logger logger = LoggerFactory.getLogger(ValidationCircuitBreakerRestClientServiceImpl.class);

    private final ValidationRestClientService validationRestClientService;

    public ValidationCircuitBreakerRestClientServiceImpl(
            ValidationRestClientService validationRestClientService) {
        this.validationRestClientService = validationRestClientService;
    }

    @Override
    //@CircuitBreaker()
    public boolean validate(String tan) {
        logger.debug("Entering validationCircuitBreakerRestClientServiceImpl.validate('{}')", tan);
        boolean result = validationRestClientService.validate(tan);
        logger.debug("Leaving validationCircuitBreakerRestClientServiceImpl with: {}", result);
        return result;
    }
}
