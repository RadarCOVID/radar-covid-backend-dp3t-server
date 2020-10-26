package org.dpppt.backend.sdk.data.gaen;

import org.dpppt.backend.sdk.model.gaen.GaenKey;
import org.dpppt.backend.sdk.model.gaen.GaenUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.*;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

public class FakeKeyService {

	private static final Long EFGS_DEFAULT_DAYS_SINCE_ONSET_OF_SYMPTOMS = 1L;
	private static final Boolean EFGS_DEFAULT_SHARING = Boolean.FALSE;


	private final GAENDataService dataService;
	private final Integer minNumOfKeys;
	private final SecureRandom random;
	private final Integer keySize;
	private final Duration retentionPeriod;
	private final boolean isEnabled;
	private final String countryOrigin;
	private final Integer reportType;

	private static final Logger logger = LoggerFactory.getLogger(FakeKeyService.class);

	public FakeKeyService(GAENDataService dataService, Integer minNumOfKeys, Integer keySize, Duration retentionPeriod,
			boolean isEnabled, String countryOrigin, Integer reportType) throws NoSuchAlgorithmException {
		this.dataService = dataService;
		this.minNumOfKeys = minNumOfKeys;
		this.random = new SecureRandom();
		this.keySize = keySize;
		this.retentionPeriod = retentionPeriod;
		this.isEnabled = isEnabled;
		this.countryOrigin = countryOrigin;
		this.reportType = reportType;
		this.updateFakeKeys();
	}

	public void updateFakeKeys() {
		deleteAllKeys();
		LocalDate currentKeyDate = LocalDate.now(ZoneOffset.UTC);
		var tmpDate = currentKeyDate.minusDays(retentionPeriod.toDays());
		logger.debug("Fill Fake keys. Start: " + currentKeyDate + " End: " + tmpDate);
		do {
			var keys = new ArrayList<GaenKey>();
			for (int i = 0; i < minNumOfKeys; i++) {
				byte[] keyData = new byte[keySize];
				random.nextBytes(keyData);
				var keyGAENTime = (int) Duration.ofSeconds(tmpDate.toEpochSecond(LocalTime.MIDNIGHT, ZoneOffset.UTC))
						.dividedBy(GaenUnit.TenMinutes.getDuration());

				var key = new GaenKey(Base64.getEncoder().encodeToString(keyData), keyGAENTime, GaenKey.GaenKeyDefaultRollingPeriod, 0,
									  countryOrigin, reportType, EFGS_DEFAULT_DAYS_SINCE_ONSET_OF_SYMPTOMS, EFGS_DEFAULT_SHARING);
				keys.add(key);
			}
			this.dataService.upsertExposees(keys);
			tmpDate = tmpDate.plusDays(1);
		} while (tmpDate.isBefore(currentKeyDate));
	}

	private void deleteAllKeys() {
		logger.debug("Delete all fake keys");
		this.dataService.cleanDB(Duration.ofDays(0));
	}

	public List<GaenKey> fillUpKeys(List<GaenKey> keys, Long publishedafter, Long keyDate) {
		if (!isEnabled) {
			return keys;
		}
		var today = LocalDate.now(ZoneOffset.UTC);
		var keyLocalDate = LocalDate.ofInstant(Instant.ofEpochMilli(keyDate), ZoneOffset.UTC);
		if (today.isEqual(keyLocalDate)) {
			return keys;
		}
		var fakeKeys = this.dataService.getSortedExposedForKeyDate(keyDate, publishedafter,
						today.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli());

		keys.addAll(fakeKeys);
		return keys;
	}
}