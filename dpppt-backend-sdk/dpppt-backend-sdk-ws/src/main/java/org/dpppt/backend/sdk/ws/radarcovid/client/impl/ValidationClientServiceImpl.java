/*
 * Copyright (c) 2020 Gobierno de España
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * SPDX-License-Identifier: MPL-2.0
 */

package org.dpppt.backend.sdk.ws.radarcovid.client.impl;

import org.dpppt.backend.sdk.ws.radarcovid.client.ValidationClientService;
import org.dpppt.backend.sdk.ws.radarcovid.client.service.ValidationRestClientService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ValidationClientServiceImpl implements ValidationClientService {

    private static final Logger logger = LoggerFactory.getLogger(ValidationClientServiceImpl.class);

    private final ValidationRestClientService validationRestClientService;

    public ValidationClientServiceImpl(ValidationRestClientService validationRestClientService) {
        this.validationRestClientService = validationRestClientService;
    }

    @Override
    public boolean validate(String tan) {
        logger.debug("Entering validationClientServiceImpl.validate('{}')", tan);
        boolean result = validationRestClientService.validate(tan);
        logger.debug("Leaving validationClientServiceImpl with: {}", result);
        return result;
    }
}
