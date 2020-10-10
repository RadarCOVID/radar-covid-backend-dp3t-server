/*
 * Copyright (c) 2020 Ubique Innovation AG <https://www.ubique.ch>
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * SPDX-License-Identifier: MPL-2.0
 */
package org.dpppt.backend.sdk.ws.controller;

import com.fasterxml.jackson.core.JsonProcessingException;

import io.jsonwebtoken.Jwts;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.dpppt.backend.sdk.data.gaen.FakeKeyService;
import org.dpppt.backend.sdk.data.gaen.GAENDataService;
import org.dpppt.backend.sdk.model.gaen.*;
import org.dpppt.backend.sdk.ws.radarcovid.annotation.Loggable;
import org.dpppt.backend.sdk.ws.radarcovid.annotation.ResponseRetention;
import org.dpppt.backend.sdk.ws.security.ValidateRequest;
import org.dpppt.backend.sdk.ws.security.ValidateRequest.InvalidDateException;
import org.dpppt.backend.sdk.ws.security.signature.ProtoSignature;
import org.dpppt.backend.sdk.ws.security.signature.ProtoSignature.ProtoSignatureWrapper;
import org.dpppt.backend.sdk.ws.util.ValidationUtils;
import org.dpppt.backend.sdk.ws.util.ValidationUtils.BadBatchReleaseTimeException;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.SignatureException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.concurrent.Callable;

@Controller
@RequestMapping("/v1/gaen")
@Tag(name = "GAEN", description = "The GAEN endpoint for the mobile clients")
/**
 * The GaenController defines the API endpoints for the mobile clients to access the GAEN functionality of the
 * red backend.
 * Clients can send new Exposed Keys, or request the existing Exposed Keys.
 */
public class GaenController {
	private static final Logger logger = LoggerFactory.getLogger(GaenController.class);
	private static final String FAKE_CODE = "112358132134";

    private static final DateTimeFormatter RFC1123_DATE_TIME_FORMATTER =
            DateTimeFormat.forPattern("EEE, dd MMM yyyy HH:mm:ss 'GMT'")
                    .withZoneUTC().withLocale(Locale.ENGLISH);

	// releaseBucketDuration is used to delay the publishing of Exposed Keys by splitting the database up into batches of keys
	// in releaseBucketDuration duration. The current batch is never published, only previous batches are published.
	private final Duration releaseBucketDuration;

	private final Duration requestTime;
	private final ValidateRequest validateRequest;
	private final ValidationUtils validationUtils;
	private final GAENDataService dataService;
	private final FakeKeyService fakeKeyService;
	private final Duration exposedListCacheControl;
	private final PrivateKey secondDayKey;
	private final ProtoSignature gaenSigner;

	public GaenController(GAENDataService dataService, FakeKeyService fakeKeyService, ValidateRequest validateRequest,
						  ProtoSignature gaenSigner, ValidationUtils validationUtils, Duration releaseBucketDuration, Duration requestTime,
						  Duration exposedListCacheControl, PrivateKey secondDayKey) {
		this.dataService = dataService;
		this.fakeKeyService = fakeKeyService;
		this.releaseBucketDuration = releaseBucketDuration;
		this.validateRequest = validateRequest;
		this.requestTime = requestTime;
		this.validationUtils = validationUtils;
		this.exposedListCacheControl = exposedListCacheControl;
		this.secondDayKey = secondDayKey;
		this.gaenSigner = gaenSigner;
	}

	@PostMapping(value = "/exposed")
	@Loggable
	@ResponseRetention(time = "application.response.retention.time.exposed")
	@Transactional
	@Operation(description = "Send exposed keys to server - includes a fix for the fact that GAEN doesn't give access to the current day's exposed key")
	@ApiResponses(value = {
			@ApiResponse(responseCode = "200", description = "The exposed keys have been stored in the database"),
			@ApiResponse(responseCode = "400", description = 
					"- Invalid base64 encoding in GaenRequest" +
                    "- negative rolling period" +
                    "- fake claim with non-fake keys"),
			@ApiResponse(responseCode = "403", description = "Authentication failed") })
	public @ResponseBody Callable<ResponseEntity<String>> addExposed(
			@Valid @RequestBody @Parameter(description = "The GaenRequest contains the SecretKey from the guessed infection date, the infection date itself, and some authentication data to verify the test result") GaenRequest gaenRequest,
			@RequestHeader(value = "User-Agent") @Parameter(description = "App Identifier (PackageName/BundleIdentifier) + App-Version + OS (Android/iOS) + OS-Version", example = "ch.ubique.android.starsdk;1.0;iOS;13.3") String userAgent,
			@AuthenticationPrincipal @Parameter(description = "JWT token that can be verified by the backend server") Object principal) {
		var now = Instant.now().toEpochMilli();
		if (!this.validateRequest.isValid(principal)) {
			return () -> ResponseEntity.status(HttpStatus.FORBIDDEN).build();
		}

		List<GaenKey> nonFakeKeys = new ArrayList<>();
		for (var key : gaenRequest.getGaenKeys()) {
			if (!validationUtils.isValidBase64Key(key.getKeyData())) {
				return () -> new ResponseEntity<>("No valid base64 key", HttpStatus.BAD_REQUEST);
			}
			if (this.validateRequest.isFakeRequest(principal, key) 
				|| hasNegativeRollingPeriod(key)
				|| hasInvalidKeyDate(principal, key)) {
				continue;
			}

			if (key.getRollingPeriod().equals(0)) {
				//currently only android seems to send 0 which can never be valid, since a non used key should not be submitted
				//default value according to EN is 144, so just set it to that. If we ever get 0 from iOS we should log it, since
				//this should not happen
				key.setRollingPeriod(GaenKey.GaenKeyDefaultRollingPeriod);
				if (userAgent.toLowerCase().contains("ios")) {
					logger.error("Received a rolling period of 0 for an iOS User-Agent");
				}
			}
			nonFakeKeys.add(key);
		}

		if (principal instanceof Jwt && ((Jwt) principal).containsClaim("fake")
				&& ((Jwt) principal).getClaimAsString("fake").equals("1")) {
			Jwt token = (Jwt) principal;
			if (FAKE_CODE.equals(token.getSubject())) {
				logger.info("Claim is fake - subject: {}", token.getSubject());
			} else if (!nonFakeKeys.isEmpty()) {
				return () -> ResponseEntity.badRequest().body("Claim is fake but list contains non fake keys");
			}
		}

		if (!nonFakeKeys.isEmpty()) {
			dataService.upsertExposees(nonFakeKeys);
		}

		var delayedKeyDateDuration = Duration.of(gaenRequest.getDelayedKeyDate(), GaenUnit.TenMinutes);
		var delayedKeyDate = LocalDate.ofInstant(Instant.ofEpochMilli(delayedKeyDateDuration.toMillis()),
				ZoneOffset.UTC);

		var nowDay = LocalDate.now(ZoneOffset.UTC);
		if (delayedKeyDate.isBefore(nowDay.minusDays(1)) || delayedKeyDate.isAfter(nowDay.plusDays(1))) {
			return () -> ResponseEntity.badRequest().body("delayedKeyDate date must be between yesterday and tomorrow");
		}

		var responseBuilder = ResponseEntity.ok();
		if (principal instanceof Jwt) {
			var originalJWT = (Jwt) principal;
			var jwtBuilder = Jwts.builder().setId(UUID.randomUUID().toString()).setIssuedAt(Date.from(Instant.now()))
					.setIssuer("dpppt-sdk-backend").setSubject(originalJWT.getSubject())
					.setExpiration(Date
							.from(delayedKeyDate.atStartOfDay().toInstant(ZoneOffset.UTC).plus(Duration.ofHours(48))))
					.claim("scope", "currentDayExposed").claim("delayedKeyDate", gaenRequest.getDelayedKeyDate());
			if (originalJWT.containsClaim("fake")) {
				jwtBuilder.claim("fake", originalJWT.getClaim("fake"));
			}
			String jwt = jwtBuilder.signWith(secondDayKey).compact();
			responseBuilder.header("Authorization", "Bearer " + jwt);
			responseBuilder.header("X-Exposed-Token", "Bearer " + jwt);
		}
		Callable<ResponseEntity<String>> cb = () -> {
			normalizeRequestTime(now);
			return responseBuilder.body("OK");
		};
		return cb;
	}

	@PostMapping(value = "/exposednextday")
	@Loggable
	@ResponseRetention(time = "application.response.retention.time.exposednextday")
	@Transactional
	@Operation(description = "Allows the client to send the last exposed key of the infection to the backend server. The JWT must come from a previous call to /exposed")
	@ApiResponses(value = {
			@ApiResponse(responseCode = "200", description = "The exposed key has been stored in the backend"),
			@ApiResponse(responseCode = "400", description = 
					"- Ivnalid base64 encoded Temporary Exposure Key" +
					"- TEK-date does not match delayedKeyDAte claim in Jwt" +
					"- TEK has negative rolling period"),
			@ApiResponse(responseCode = "403", description = "No delayedKeyDate claim in authentication") })
	public @ResponseBody Callable<ResponseEntity<String>> addExposedSecond(
			@Valid @RequestBody @Parameter(description = "The last exposed key of the user") GaenSecondDay gaenSecondDay,
			@RequestHeader(value = "User-Agent") @Parameter(description = "App Identifier (PackageName/BundleIdentifier) + App-Version + OS (Android/iOS) + OS-Version", example = "ch.ubique.android.starsdk;1.0;iOS;13.3") String userAgent,
			@AuthenticationPrincipal @Parameter(description = "JWT token that can be verified by the backend server, must have been created by /v1/gaen/exposed and contain the delayedKeyDate") Object principal) {
		var now = Instant.now().toEpochMilli();

		if (!validationUtils.isValidBase64Key(gaenSecondDay.getDelayedKey().getKeyData())) {
			return () -> new ResponseEntity<>("No valid base64 key", HttpStatus.BAD_REQUEST);
		}
		if (principal instanceof Jwt && !((Jwt) principal).containsClaim("delayedKeyDate")) {
			return () -> ResponseEntity.status(HttpStatus.FORBIDDEN).body("claim does not contain delayedKeyDate");
		}
		if (principal instanceof Jwt) {
			var jwt = (Jwt) principal;
			var claimKeyDate = Integer.parseInt(jwt.getClaimAsString("delayedKeyDate"));
			if (!gaenSecondDay.getDelayedKey().getRollingStartNumber().equals(claimKeyDate)) {
				return () -> ResponseEntity.badRequest().body("keyDate does not match claim keyDate");
			}
		}

		if (!this.validateRequest.isFakeRequest(principal, gaenSecondDay.getDelayedKey())) {
			if (gaenSecondDay.getDelayedKey().getRollingPeriod().equals(0)) {
				// currently only android seems to send 0 which can never be valid, since a non used key should not be submitted
				// default value according to EN is 144, so just set it to that. If we ever get 0 from iOS we should log it, since
				// this should not happen
				gaenSecondDay.getDelayedKey().setRollingPeriod(GaenKey.GaenKeyDefaultRollingPeriod);
				if(userAgent.toLowerCase().contains("ios")) {
					logger.error("Received a rolling period of 0 for an iOS User-Agent");
				}
			} else if(gaenSecondDay.getDelayedKey().getRollingPeriod() < 0) {
				return () -> ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Rolling Period MUST NOT be negative.");
			}
			List<GaenKey> keys = new ArrayList<>();
			keys.add(gaenSecondDay.getDelayedKey());
			dataService.upsertExposees(keys);
		}

		return () -> {
			normalizeRequestTime(now);
			return ResponseEntity.ok().body("OK");
		};

	}

	@GetMapping(value = "/exposed/{keyDate}", produces = "application/zip")
	@Loggable
	@Operation(description = "Request the exposed key from a given date")
	@ApiResponses(value = {
			@ApiResponse(responseCode = "200", description = "zipped export.bin and export.sig of all keys in that interval"),
			@ApiResponse(responseCode = "400", description = 
					"- invalid starting key date, doesn't point to midnight UTC" +
					"- _publishedAfter_ is not at the beginning of a batch release time, currently 2h")})
	public @ResponseBody ResponseEntity<byte[]> getExposedKeys(
			@PathVariable @Parameter(description = "Requested date for Exposed Keys retrieval, in milliseconds since Unix epoch (1970-01-01). It must indicate the beginning of a TEKRollingPeriod, currently midnight UTC.", example = "1593043200000") long keyDate,
			@RequestParam(required = false) @Parameter(description = "Restrict returned Exposed Keys to dates after this parameter. Given in milliseconds since Unix epoch (1970-01-01).", example = "1593043200000") Long publishedafter)
			throws BadBatchReleaseTimeException, IOException, InvalidKeyException, SignatureException,
			NoSuchAlgorithmException {
		if (!validationUtils.isValidKeyDate(keyDate)) {
			return ResponseEntity.notFound().build();
		}
		if (publishedafter != null && !validationUtils.isValidBatchReleaseTime(publishedafter)) {
			return ResponseEntity.notFound().build();
		}

		long now = System.currentTimeMillis();
		// calculate exposed until bucket
		long publishedUntil = now - (now % releaseBucketDuration.toMillis());
		DateTime dateTime = new DateTime(publishedUntil + releaseBucketDuration.toMillis() - 1, DateTimeZone.UTC);
		
		var exposedKeys = dataService.getSortedExposedForKeyDate(keyDate, publishedafter, publishedUntil);
		exposedKeys = fakeKeyService.fillUpKeys(exposedKeys, publishedafter, keyDate);
		if (exposedKeys.isEmpty()) {
			return ResponseEntity.noContent()//.cacheControl(CacheControl.maxAge(exposedListCacheControl))
					.header("X-PUBLISHED-UNTIL", Long.toString(publishedUntil))
					.header("Expires", RFC1123_DATE_TIME_FORMATTER.print(dateTime))
					.build();
		}

		ProtoSignatureWrapper payload = gaenSigner.getPayload(exposedKeys);
		
		return ResponseEntity.ok()//.cacheControl(CacheControl.maxAge(exposedListCacheControl))
				.header("X-PUBLISHED-UNTIL", Long.toString(publishedUntil))
				.header("Expires", RFC1123_DATE_TIME_FORMATTER.print(dateTime))
				.body(payload.getZip());
	}

	@GetMapping(value = "/buckets/{dayDateStr}")
	@Loggable
	@Operation(description = "Request the available release batch times for a given day")
	@ApiResponses(value = {
			@ApiResponse(responseCode = "200", description = "zipped export.bin and export.sig of all keys in that interval"),
			@ApiResponse(responseCode = "400", description = "invalid starting key date, points outside of the retention range")})
	public @ResponseBody ResponseEntity<DayBuckets> getBuckets(
			@PathVariable @Parameter(description = "Starting date for exposed key retrieval, as ISO-8601 format", example = "2020-06-27") String dayDateStr) {
		var atStartOfDay = LocalDate.parse(dayDateStr).atStartOfDay().toInstant(ZoneOffset.UTC)
				.atOffset(ZoneOffset.UTC);
		var end = atStartOfDay.plusDays(1);
		var now = Instant.now().atOffset(ZoneOffset.UTC);
		if (!validationUtils.isDateInRange(atStartOfDay)) {
			return ResponseEntity.notFound().build();
		}
		var relativeUrls = new ArrayList<String>();
		var dayBuckets = new DayBuckets();

		String controllerMapping = this.getClass().getAnnotation(RequestMapping.class).value()[0];
		dayBuckets.setDay(dayDateStr).setRelativeUrls(relativeUrls).setDayTimestamp(atStartOfDay.toInstant().toEpochMilli());

		while (atStartOfDay.toInstant().toEpochMilli() < Math.min(now.toInstant().toEpochMilli(),
				end.toInstant().toEpochMilli())) {
			relativeUrls.add(controllerMapping + "/exposed" + "/" + atStartOfDay.toInstant().toEpochMilli());
			atStartOfDay = atStartOfDay.plus(this.releaseBucketDuration);
		}

		return ResponseEntity.ok(dayBuckets);
	}

	private void normalizeRequestTime(long now) {
		long after = Instant.now().toEpochMilli();
		long duration = after - now;
		try {
			Thread.sleep(Math.max(requestTime.minusMillis(duration).toMillis(), 0));
		} catch (Exception ex) {
			logger.error("Couldn't equalize request time: {}", ex.toString());
		}
	}

	private boolean hasNegativeRollingPeriod(GaenKey key) {
		Integer rollingPeriod = key.getRollingPeriod();
		if (key.getRollingPeriod() < 0) {
			logger.error("Detected key with negative rolling period {}", rollingPeriod);
			return true;
		} else {
			return false;
		}
	}

	private boolean hasInvalidKeyDate(Object principal, GaenKey key) {
		try { 
			this.validateRequest.getKeyDate(principal, key);
		}
		catch (InvalidDateException invalidDate) {
			logger.error(invalidDate.getLocalizedMessage());
			return true;
		}
		return false;
	}

	@ExceptionHandler({IllegalArgumentException.class, InvalidDateException.class, JsonProcessingException.class,
			MethodArgumentNotValidException.class, BadBatchReleaseTimeException.class, DateTimeParseException.class})
	@ResponseStatus(HttpStatus.BAD_REQUEST)
    public ResponseEntity<Object> invalidArguments(Exception ex) {
        logger.error("Exception ({}): {}", ex.getClass().getSimpleName(), ex.getMessage(), ex);
		return ResponseEntity.badRequest().build();
	}
}