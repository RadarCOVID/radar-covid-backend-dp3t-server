/*
 * Copyright (c) 2020 Ubique Innovation AG <https://www.ubique.ch>
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * SPDX-License-Identifier: MPL-2.0
 */

package org.dpppt.backend.sdk.data.gaen;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import javax.sql.DataSource;
import org.dpppt.backend.sdk.model.gaen.GaenKey;
import org.dpppt.backend.sdk.model.gaen.GaenUnit;
import org.dpppt.backend.sdk.utils.UTCInstant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

public class JDBCGAENDataServiceImpl implements GAENDataService {

  private static final Logger logger = LoggerFactory.getLogger(JDBCGAENDataServiceImpl.class);

  private static final String PGSQL = "pgsql";
  private final String dbType;
  private final NamedParameterJdbcTemplate jt;
  private final Duration releaseBucketDuration;
  // Time skew means the duration for how long a key still is valid __after__ it has expired (e.g 2h
  // for now
  // https://developer.apple.com/documentation/exposurenotification/setting_up_a_key_server?language=objc)
  private final Duration timeSkew;

  public JDBCGAENDataServiceImpl(
      String dbType, DataSource dataSource, Duration releaseBucketDuration, Duration timeSkew) {
    this.dbType = dbType;
    this.jt = new NamedParameterJdbcTemplate(dataSource);
    this.releaseBucketDuration = releaseBucketDuration;
    this.timeSkew = timeSkew;
  }

  @Override
  @Transactional(readOnly = false)
  public void upsertExposees(List<GaenKey> gaenKeys, UTCInstant now) {
    upsertExposeesDelayed(gaenKeys, null, now);
  }

  @Override
  public void upsertExposeesDelayed(
      List<GaenKey> gaenKeys, UTCInstant delayedReceivedAt, UTCInstant now) {

    String sql = null;
    if (dbType.equals(PGSQL)) {
      sql =
          "insert into t_gaen_exposed (key, rolling_start_number, rolling_period,"
              + " transmission_risk_level, received_at,"
              + " country_origin, report_type, days_since_onset, efgs_sharing, expiry)"
              + " values (:key, :rolling_start_number,"
              + " :rolling_period, :transmission_risk_level, :received_at,"
              + " :country_origin, :report_type, :days_since_onset, :efgs_sharing, :expiry)"
              + " on conflict on constraint gaen_exposed_key do nothing";
    } else {
      sql =
          "merge into t_gaen_exposed using (values(cast(:key as varchar(24)),"
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
    }
    var parameterList = new ArrayList<MapSqlParameterSource>();
    // Calculate the `receivedAt` just at the end of the current releaseBucket.
    var receivedAt =
        delayedReceivedAt == null
            ? now.roundToNextBucket(releaseBucketDuration).minus(Duration.ofMillis(1))
            : delayedReceivedAt;
    for (var gaenKey : gaenKeys) {
      var exiry = UTCInstant.of(gaenKey.getRollingStartNumber() + gaenKey.getRollingPeriod(), GaenUnit.TenMinutes).plus(timeSkew);
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
      params.addValue("expiry", exiry.getDate());

      parameterList.add(params);
    }
    jt.batchUpdate(sql, parameterList.toArray(new MapSqlParameterSource[0]));
  }

  @Override
  @Transactional(readOnly = true)
  public List<GaenKey> getSortedExposedForKeyDate(
      UTCInstant keyDate, UTCInstant publishedAfter, UTCInstant publishedUntil, UTCInstant now) {
    MapSqlParameterSource params = new MapSqlParameterSource();
    params.addValue("rollingPeriodStartNumberStart", keyDate.get10MinutesSince1970());
    params.addValue("rollingPeriodStartNumberEnd", keyDate.plusDays(1).get10MinutesSince1970());
    params.addValue("publishedUntil", publishedUntil.getDate());

    StringBuilder sql = new StringBuilder(
        "select pk_exposed_id, key, rolling_start_number, rolling_period, transmission_risk_level"
            + " from t_gaen_exposed where rolling_start_number >= :rollingPeriodStartNumberStart"
            + " and rolling_start_number < :rollingPeriodStartNumberEnd and received_at <"
            + " :publishedUntil");

    // we need to subtract the time skew since we want to release it iff rolling_start_number +
    // rolling_period + timeSkew < NOW
    // note though that since we use `<` instead of `<=` a key which is valid until 24:00 will be
    // accepted until 02:00 (by the clients, so we MUST NOT release it before 02:00), but 02:00 lies
    // in the bucket of 04:00. So the key will be released
    // earliest 04:00.
    params.addValue(
        "maxAllowedStartNumber",
        now.roundToBucketStart(releaseBucketDuration).minus(timeSkew).get10MinutesSince1970());
    sql.append(" and rolling_start_number + rolling_period < :maxAllowedStartNumber");

    // note that received_at is always rounded to `next_bucket` - 1ms to difuse actual upload time
    if (publishedAfter != null) {
      params.addValue("publishedAfter", publishedAfter.getDate());
      sql.append(" and received_at >= :publishedAfter");
    }

    sql.append(" order by pk_exposed_id desc");

    return jt.query(sql.toString(), params, new GaenKeyRowMapper());
  }

  @Override
  @Transactional(readOnly = true)
  public List<GaenKey> getSortedExposedSince(UTCInstant keysSince, UTCInstant now, List<String> visitedCountries,
                                             List<String> originCountries) {
    MapSqlParameterSource params = new MapSqlParameterSource();
    params.addValue("since", keysSince.getDate());
    params.addValue("maxBucket", now.roundToBucketStart(releaseBucketDuration).getDate());
    params.addValue("timeSkewSeconds", timeSkew.toSeconds());

    // Select keys since the given date. We need to make sure, only keys are returned
    // that are allowed to be published.
    // For this, we calculate the expiry for each key in a sub query. The expiry is then used for
    // the where clause:
    // - if expiry <= received_at: the key was ready to publish when we received it. Release this
    // key, if received_at in [since, maxBucket)
    // - if expiry > received_at: we have to wait until expiry till we can release this key. This
    // means we only release the key if expiry in [since, maxBucket)
    // This problem arises, because we only want key with received_at after since, but we need to
    // ensure that we relase ALL keys meaning keys which were still valid when they were received

    // we need to add the time skew to calculate the expiry timestamp of a key:
    // TO_TIMESTAMP((rolling_start_number + rolling_period) * 10 * 60 + :timeSkewSeconds

    // INIT RADARCOVID efficiency changes:
    // - expiry timestamp is setting on creation key as column
    // - different queries for visited countries and no visited
    //  
    //    String sql =
    //            "select keys.pk_exposed_id, keys.key, keys.rolling_start_number, keys.rolling_period,"
    //                + " keys.transmission_risk_level from (select pk_exposed_id, key,"
    //                + " rolling_start_number, rolling_period, transmission_risk_level, received_at,  "
    //                + getSQLExpressionForExpiry()
    //                + " as expiry from t_gaen_exposed) as keys where ( (keys.expiry <= keys.received_at"
    //                + " AND keys.received_at >= :since AND keys.received_at < :maxBucket) OR (keys.expiry"
    //                + " > keys.received_at AND keys.expiry >= :since AND keys.expiry < :maxBucket) )"
    //                + " order by keys.pk_exposed_id desc";

    StringBuilder sql = new StringBuilder()
    		.append("select distinct keys.pk_exposed_id, keys.key, keys.rolling_start_number, keys.rolling_period, ")
    		.append("keys.transmission_risk_level from t_gaen_exposed as keys ");
    
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
    // END RADARCOVID efficiency changes

    return jt.query(sql.toString(), params, new GaenKeyRowMapper());
  }

  private String getSQLExpressionForExpiry() {
    if (this.dbType.equals(PGSQL)) {
      return "TO_TIMESTAMP((rolling_start_number + rolling_period) * 10 * 60 +"
          + " :timeSkewSeconds)";
    } else {
      return "TIMESTAMP_WITH_ZONE((rolling_start_number + rolling_period) * 10 * 60 +"
          + " :timeSkewSeconds)";
    }
  }

  @Override
  @Transactional(readOnly = false)
  public void cleanDB(Duration retentionPeriod) {
    var retentionTime = UTCInstant.now().minus(retentionPeriod);
    logger.info("Cleanup DB entries before: " + retentionTime);
    MapSqlParameterSource params =
        new MapSqlParameterSource("retention_time", retentionTime.getDate());
    String sqlExposed = "delete from t_gaen_exposed where received_at < :retention_time";
    jt.update(sqlExposed, params);
  }
}
