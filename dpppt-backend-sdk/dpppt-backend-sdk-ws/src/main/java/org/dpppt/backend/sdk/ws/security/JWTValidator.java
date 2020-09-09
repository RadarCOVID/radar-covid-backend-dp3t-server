/*
 * Copyright (c) 2020 Ubique Innovation AG <https://www.ubique.ch>
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * SPDX-License-Identifier: MPL-2.0
 */

package org.dpppt.backend.sdk.ws.security;

import java.time.Duration;
import java.time.Instant;

import org.dpppt.backend.sdk.data.RedeemDataService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;


public class JWTValidator implements OAuth2TokenValidator<Jwt> {

    public static final String UUID_CLAIM = "jti";

    private static final Logger logger = LoggerFactory.getLogger(JWTValidator.class);

    private final RedeemDataService dataService;
    private final Duration maxJwtValidity;

    public JWTValidator(RedeemDataService dataService, Duration maxJwtValidity) {
        this.dataService = dataService;
        this.maxJwtValidity = maxJwtValidity;
    }

    @Override
    public OAuth2TokenValidatorResult validate(Jwt token) {
        if (token.getExpiresAt() == null || Instant.now().plus(maxJwtValidity).isBefore(token.getExpiresAt())) {
            if (logger.isDebugEnabled())
                logger.debug("token invalid -> expiration: {}", tokenToString(token));
            return OAuth2TokenValidatorResult.failure(new OAuth2Error(OAuth2ErrorCodes.INVALID_REQUEST));
        }
        if (token.containsClaim(UUID_CLAIM) && this.dataService.checkAndInsertPublishUUID(token.getClaim(UUID_CLAIM))) {
            if (logger.isDebugEnabled())
                logger.debug("token valid -> uuid: {}", tokenToString(token));
            return OAuth2TokenValidatorResult.success();
        }
        if (logger.isDebugEnabled())
            logger.debug("token invalid -> invalid scope: {}", tokenToString(token));
        return OAuth2TokenValidatorResult.failure(new OAuth2Error(OAuth2ErrorCodes.INVALID_SCOPE));
    }

    public String tokenToString(Jwt token) {
        String result = "";
        if (token != null) {
            StringBuilder stringBuilder = new StringBuilder("Jwt{");
            stringBuilder.append("[ID]:").append(token.getId());
            stringBuilder.append("|[SUBJECT]:").append(token.getSubject());
            stringBuilder.append("|[ISSUED_AT]:").append(token.getIssuedAt());
            stringBuilder.append("|[EXPIRES_AT]:").append(token.getExpiresAt());
            //stringBuilder.append("|[HEADERS]:").append(token.getHeaders());
            //stringBuilder.append("|[CLAIMS]:").append(token.getClaims());
            stringBuilder.append("}");
            result = stringBuilder.toString();
        }
        return result;
    }

}
