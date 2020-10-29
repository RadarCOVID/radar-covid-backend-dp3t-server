/*
 * Copyright (c) 2020 Ubique Innovation AG <https://www.ubique.ch>
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * SPDX-License-Identifier: MPL-2.0
 */

package org.dpppt.backend.sdk.ws.security.gaen;

import org.apache.commons.lang3.StringUtils;
import org.dpppt.backend.sdk.model.gaen.GaenKey;
import org.dpppt.backend.sdk.model.gaen.GaenUnit;
import org.dpppt.backend.sdk.ws.radarcovid.client.ValidationClientService;
import org.dpppt.backend.sdk.ws.security.ValidateRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

public class JWTValidateRequest implements ValidateRequest {

    private static final Logger logger = LoggerFactory.getLogger(JWTValidateRequest.class);

    private static final String CLAIM_SCOPE = "scope";
    private static final String CLAIM_TAN = "tan";

    @Value("${application.endpoint.validation.enabled:true}")
    private boolean validationEnabled;

    // FIXME Quitar
    @Value("${application.jwt.validate.fake.enabled:true}")
    private boolean fakeEnabled;

    private final ValidationClientService validationClientService;

    public JWTValidateRequest(ValidationClientService validationClientService) {
        this.validationClientService = validationClientService;
    }

    @Override
    public boolean isValid(Object authObject) {
        if (authObject instanceof Jwt) {
            Jwt token = (Jwt) authObject;
            boolean checkTan = checkTanClaim(token);
            return token.containsClaim(CLAIM_SCOPE) && token.getClaim(CLAIM_SCOPE).equals("exposed") && checkTan;
        }
        return false;
    }

    private boolean checkTanClaim(Jwt token) {
        boolean result = true;
        boolean isFake = isFakeToken(token);
        String tan = token.containsClaim(CLAIM_TAN) ? (String) token.getClaim(CLAIM_TAN) : "";
        if (validationEnabled && !isFake) {
            result = StringUtils.isNoneEmpty(tan) && validationClientService.validate(tan);
            logger.debug("Verify tan ({}) = {}", tan, result); // Only for debugging purposes
        }
        return result;
    }

    @Override
    public long getKeyDate(Object authObject, Object others) throws InvalidDateException {
        if (authObject instanceof Jwt) {
            Jwt token = (Jwt) authObject;
            long jwtKeyDate = LocalDate.parse(token.getClaim("onset")).atStartOfDay().atOffset(ZoneOffset.UTC).toInstant().toEpochMilli();
            if (others instanceof GaenKey) {
                GaenKey request = (GaenKey) others;
                var keyDate = Duration.of(request.getRollingStartNumber(), GaenUnit.TenMinutes);
                if (keyDate.toMillis() > System.currentTimeMillis()) {
                    throw new InvalidDateException("keyDate > currentTimeMillis");
                } else if (keyDate.toMillis() < jwtKeyDate) {
                    throw new InvalidDateException("keyDate < onset");
                } else if (keyDate.toMillis() < OffsetDateTime.now().minusDays(21).toInstant().toEpochMilli()) {
                    throw new InvalidDateException("keyDate < now - 21 days");
                }
                jwtKeyDate = keyDate.toMillis();
            }
            return jwtKeyDate;
        }
        throw new IllegalArgumentException();
    }

    @Override
    public boolean isFakeRequest(Object authObject, Object others) {
        if (authObject instanceof Jwt && others instanceof GaenKey) {
            Jwt token = (Jwt) authObject;
            GaenKey request = (GaenKey) others;
            boolean fake = false;
            if (isFakeToken(token)) {
                fake = true;
            }
            if (request.getFake() == 1) {
                fake = true;
            }

            // FIXME: Quitar una vez que apps desplieguen nueva versión
            if (fakeEnabled && !fake && token.containsClaim("onset") && token.getClaim("onset") != null) {
                long jwtKeyDate = LocalDate.parse(token.getClaim("onset")).minusDays(1).atStartOfDay().atOffset(ZoneOffset.UTC).toInstant().toEpochMilli();
                var keyDate = Duration.of(request.getRollingStartNumber(), GaenUnit.TenMinutes);
                if (keyDate.toMillis() <= jwtKeyDate) {
                    fake = true;
                }
            }

            return fake;
        }
        throw new IllegalArgumentException();
    }

    private boolean isFakeToken(Jwt token) {
        return token != null && (token.containsClaim("fake") && token.getClaimAsString("fake").equals("1"));
    }

}