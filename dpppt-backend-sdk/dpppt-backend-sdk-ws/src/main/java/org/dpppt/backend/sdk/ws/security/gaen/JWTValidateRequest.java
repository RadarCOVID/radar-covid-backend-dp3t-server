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
import org.dpppt.backend.sdk.utils.UTCInstant;
import org.dpppt.backend.sdk.ws.radarcovid.client.ValidationClientService;
import org.dpppt.backend.sdk.ws.security.ValidateRequest;
import org.dpppt.backend.sdk.ws.util.ValidationUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jwt.Jwt;

public class JWTValidateRequest implements ValidateRequest {

  private static final Logger logger = LoggerFactory.getLogger(JWTValidateRequest.class);

  private static final String CLAIM_SCOPE = "scope";
  private static final String CLAIM_TAN = "tan";
  private static final String CLAIM_FAKE = "fake";

  @Value("${application.endpoint.validation.enabled:true}")
  private boolean validationEnabled;

  private final ValidationUtils validationUtils;
  private final ValidationClientService validationClientService;

  public JWTValidateRequest(ValidationUtils validationUtils, ValidationClientService validationClientService) {
    this.validationUtils = validationUtils;
    this.validationClientService = validationClientService;
  }

  @Override
  public boolean isValid(Object authObject) throws WrongScopeException {
    if (authObject instanceof Jwt) {
      Jwt token = (Jwt) authObject;
      if (Boolean.TRUE.equals(token.containsClaim(CLAIM_SCOPE))
          && token.getClaim(CLAIM_SCOPE).equals("exposed") && checkTanClaim(token)) {
        return true;
      }
      throw new WrongScopeException();
    }
    return false;
  }

  private boolean checkTanClaim(Jwt token) {
    boolean result = true;
    boolean isFake = isFakeToken(token);
    if (validationEnabled && !isFake) {
      String tan = token.containsClaim(CLAIM_TAN) ? (String) token.getClaim(CLAIM_TAN) : "";
      result = StringUtils.isNoneEmpty(tan) && validationClientService.validate(tan);
      logger.debug("Verify tan ({}) = {}", tan, result); // Only for debugging purposes
    }
    return result;
  }

  @Override
  public long validateKeyDate(UTCInstant now, Object authObject, Object others)
      throws ClaimIsBeforeOnsetException {
    if (authObject instanceof Jwt) {
      Jwt token = (Jwt) authObject;
      var jwtKeyDate = UTCInstant.parseDate(token.getClaim("onset"));
      if (others instanceof GaenKey) {
        GaenKey request = (GaenKey) others;
        var keyDate = UTCInstant.of(request.getRollingStartNumber(), GaenUnit.TenMinutes);
        if (keyDate.isBeforeEpochMillisOf(jwtKeyDate)) {
          throw new ClaimIsBeforeOnsetException();
        }
        jwtKeyDate = keyDate;
      }
      return jwtKeyDate.getTimestamp();
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
      return fake;
    }
    throw new IllegalArgumentException();
  }

  private boolean isFakeToken(Jwt token) {
    return token != null && (token.containsClaim(CLAIM_FAKE) && token.getClaimAsString(CLAIM_FAKE).equals("1"));
  }

}
