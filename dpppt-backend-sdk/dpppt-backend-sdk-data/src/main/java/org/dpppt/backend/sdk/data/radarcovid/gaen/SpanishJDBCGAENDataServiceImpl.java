/*
 * Copyright (c) 2020 Ubique Innovation AG <https://www.ubique.ch>
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * SPDX-License-Identifier: MPL-2.0
 */

package org.dpppt.backend.sdk.data.radarcovid.gaen;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import javax.sql.DataSource;

import org.dpppt.backend.sdk.data.gaen.GAENDataService;
import org.dpppt.backend.sdk.data.gaen.GaenKeyRowMapper;
import org.dpppt.backend.sdk.data.gaen.JDBCGAENDataServiceImpl;
import org.dpppt.backend.sdk.model.gaen.GaenKey;
import org.dpppt.backend.sdk.model.gaen.GaenUnit;
import org.dpppt.backend.sdk.utils.UTCInstant;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.transaction.annotation.Transactional;

public class SpanishJDBCGAENDataServiceImpl extends JDBCGAENDataServiceImpl implements GAENDataService {
	

	public SpanishJDBCGAENDataServiceImpl(String dbType, DataSource dataSource, Duration releaseBucketDuration,
			Duration timeSkew) {
		super(dbType, dataSource, releaseBucketDuration, timeSkew);
	}

	@Override
	public void upsertExposeesDelayed(List<GaenKey> gaenKeys, UTCInstant delayedReceivedAt, UTCInstant now) {
		// Calculate the `receivedAt` just at the end of the current releaseBucket.
		var receivedAt = delayedReceivedAt == null
				? now.roundToNextBucket(releaseBucketDuration).minus(Duration.ofMillis(1))
				: delayedReceivedAt;
		for (var gaenKey : gaenKeys) {
			internalUpsertKey(gaenKey, receivedAt);
		}
	}

	@Override
	@Transactional(readOnly = true)
	public List<GaenKey> getSortedExposedSince(UTCInstant keysSince, UTCInstant now, List<String> visitedCountries,
			List<String> originCountries) {
		MapSqlParameterSource params = new MapSqlParameterSource();
		params.addValue("since", keysSince.getDate());
		params.addValue("maxBucket", now.roundToBucketStart(releaseBucketDuration).getDate());

		StringBuilder sql = new StringBuilder().append(
				"select distinct keys.pk_exposed_id, keys.key, keys.rolling_start_number, keys.rolling_period, ")
				.append("keys.transmission_risk_level, keys.report_type, keys.days_since_onset from t_gaen_exposed as keys ");

		if (visitedCountries != null && !visitedCountries.isEmpty()) {
			sql.append("inner join t_visited as visited on pk_exposed_id = pfk_exposed_id ");
		}

		sql.append("where ((keys.expiry <= keys.received_at and keys.received_at >= :since and keys.received_at < ")
				.append(":maxBucket) or (keys.expiry > keys.received_at and keys.expiry >= :since and keys.expiry < :maxBucket)) ");

		if (originCountries != null && !originCountries.isEmpty()) {
			sql.append("and keys.country_origin in (:originc) ");
			params.addValue("originc", originCountries);
		}

		if (visitedCountries != null && !visitedCountries.isEmpty()) {
			sql.append("and visited.country in (:visitedc) ");
			params.addValue("visitedc", visitedCountries);
		}

		sql.append("order by keys.pk_exposed_id desc");

		return jt.query(sql.toString(), params, new GaenKeyRowMapper());
	}

	private void internalUpsertKey(GaenKey gaenKey, UTCInstant receivedAt) {

		var expiry = UTCInstant.of(gaenKey.getRollingStartNumber() + gaenKey.getRollingPeriod(), GaenUnit.TenMinutes)
				.plus(timeSkew);

		String sqlKey = null;
		String sqlVisited = null;
		if (dbType.equals(PGSQL)) {
			sqlKey = "insert into t_gaen_exposed (key, rolling_start_number, rolling_period,"
					+ " transmission_risk_level, received_at,"
					+ " country_origin, report_type, days_since_onset, efgs_sharing, expiry)"
					+ " values (:key, :rolling_start_number,"
					+ " :rolling_period, :transmission_risk_level, :received_at,"
					+ " :country_origin, :report_type, :days_since_onset, :efgs_sharing, :expiry)"
					+ " on conflict on constraint gaen_exposed_key do nothing";
			sqlVisited = "insert into t_visited (pfk_exposed_id, country) values (:keyId, :country) on conflict on"
					+ " constraint pk_t_visited do nothing";
		} else {
			sqlKey = "merge into t_gaen_exposed using (values(cast(:key as varchar(24)),"
					+ " :rolling_start_number, :rolling_period, :transmission_risk_level, :received_at,"
					+ " :country_origin, :report_type, :days_since_onset, :efgs_sharing, :expiry))"
					+ " as vals(key, rolling_start_number, rolling_period, transmission_risk_level,"
					+ " received_at, country_origin, report_type, days_since_onset, efgs_sharing, expiry)"
					+ " on t_gaen_exposed.key = vals.key when not matched then insert (key,"
					+ " rolling_start_number, rolling_period, transmission_risk_level, received_at,"
					+ " country_origin, report_type, days_since_onset, efgs_sharing, expiry)"
					+ " values (vals.key, vals.rolling_start_number, vals.rolling_period,"
					+ " transmission_risk_level, vals.received_at,"
					+ " vals.country_origin, vals.report_type, vals.days_since_onset, vals.efgs_sharing, vals.expiry)";
			sqlVisited = "merge into t_visited using (values(:keyId, :country)) as vals(keyId, country) on"
					+ " t_visited.pfk_exposed_id = vals.keyId and t_visited.country = vals.country when"
					+ " not matched then insert (pfk_exposed_id, country) values (vals.keyId, vals.country)";
		}

		List<MapSqlParameterSource> visitedBatch = new ArrayList<>();
		MapSqlParameterSource params = new MapSqlParameterSource();
		params.addValue("key", gaenKey.getKeyData());
		params.addValue("rolling_start_number", gaenKey.getRollingStartNumber());
		params.addValue("rolling_period", gaenKey.getRollingPeriod());
		params.addValue("transmission_risk_level", gaenKey.getTransmissionRiskLevel());
		params.addValue("received_at", receivedAt.getDate());
		params.addValue("country_origin", gaenKey.getCountryOrigin());
		params.addValue("report_type", gaenKey.getReportType());
		params.addValue("days_since_onset", gaenKey.getDaysSinceOnsetOfSymptons());
		params.addValue("efgs_sharing", gaenKey.getEfgsSharing());
		params.addValue("expiry", expiry.getDate());
		KeyHolder keyHolder = new GeneratedKeyHolder();
		jt.update(sqlKey, params, keyHolder);

		// if the key already exists, no ids are returned. in this case we assume that
		// we do not need to modify the visited countries also
		if (keyHolder.getKeys() != null && !keyHolder.getKeys().isEmpty()) {
			Object keyObject = keyHolder.getKeys().get("pk_exposed_id");
			if (keyObject != null) {
				int gaenKeyId = ((Integer) keyObject).intValue();
				for (String country : gaenKey.getVisitedCountries()) {
					MapSqlParameterSource visitedParams = new MapSqlParameterSource();
					visitedParams.addValue("keyId", gaenKeyId);
					visitedParams.addValue("country", country);
					visitedBatch.add(visitedParams);
				}
			}
		}
		if (!visitedBatch.isEmpty()) {
			jt.batchUpdate(sqlVisited, visitedBatch.toArray(new MapSqlParameterSource[visitedBatch.size()]));
		}
	}
}
