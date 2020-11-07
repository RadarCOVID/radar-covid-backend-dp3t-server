/*
 * Copyright (c) 2020 Ubique Innovation AG <https://www.ubique.ch>
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * SPDX-License-Identifier: MPL-2.0
 */

package org.dpppt.backend.sdk.ws.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.apache.commons.lang3.StringUtils;
import org.dpppt.backend.sdk.data.gaen.DebugGAENDataService;
import org.dpppt.backend.sdk.data.gaen.DebugJDBCGAENDataServiceImpl;
import org.dpppt.backend.sdk.ws.controller.DebugController;
import org.dpppt.backend.sdk.ws.insertmanager.InsertManager;
import org.dpppt.backend.sdk.ws.insertmanager.insertionfilters.*;
import org.dpppt.backend.sdk.ws.security.KeyVault;
import org.dpppt.backend.sdk.ws.security.KeyVault.PrivateKeyNoSuitableEncodingFoundException;
import org.dpppt.backend.sdk.ws.security.KeyVault.PublicKeyNoSuitableEncodingFoundException;
import org.dpppt.backend.sdk.ws.security.ValidateRequest;
import org.dpppt.backend.sdk.ws.security.signature.ProtoSignature;
import org.dpppt.backend.sdk.ws.util.ValidationUtils;
import org.flywaydb.core.Flyway;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.retry.annotation.EnableRetry;

import javax.sql.DataSource;
import java.time.Duration;
import java.util.Arrays;
import java.util.Base64;
import java.util.Properties;

@Configuration
@Profile({"radarcovid-local", "radarcovid-pre", "radarcovid-pro"})
@EnableRetry
public class WSRadarCovidConfig extends WSBaseConfig {

  @Value("${datasource.username}")
  String dataSourceUser;

  @Value("${datasource.password}")
  String dataSourcePassword;

  @Value("${datasource.url}")
  String dataSourceUrl;

  @Value("${datasource.schema:}")
  String dataSourceSchema;

  @Value("${datasource.driverClassName}")
  String dataSourceDriver;

  @Value("${datasource.failFast}")
  String dataSourceFailFast;

  @Value("${datasource.minimumIdle}")
  int dataSourceMinimumIdle;

  @Value("${datasource.maximumPoolSize}")
  int dataSourceMaximumPoolSize;

  @Value("${datasource.maxLifetime}")
  int dataSourceMaxLifetime;

  @Value("${datasource.idleTimeout}")
  int dataSourceIdleTimeout;

  @Value("${datasource.connectionTimeout}")
  int dataSourceConnectionTimeout;

  @Value("${ws.ecdsa.credentials.privateKey:}")
  private String privateKey;

  @Value("${ws.ecdsa.credentials.publicKey:}")
  public String publicKey;

  @Bean(destroyMethod = "close")
  public DataSource dataSource() {
    HikariConfig config = new HikariConfig();
    Properties props = new Properties();
    props.put("url", dataSourceUrl);
    props.put("user", dataSourceUser);
    props.put("password", dataSourcePassword);
    if (StringUtils.isNotEmpty(dataSourceSchema))
      config.setSchema(dataSourceSchema);
    config.setDataSourceProperties(props);
    config.setDataSourceClassName(dataSourceDriver);
    config.setMinimumIdle(dataSourceMinimumIdle);
    config.setMaximumPoolSize(dataSourceMaximumPoolSize);
    config.setMaxLifetime(dataSourceMaxLifetime);
    config.setIdleTimeout(dataSourceIdleTimeout);
    config.setConnectionTimeout(dataSourceConnectionTimeout);
    return new HikariDataSource(config);
  }

  @Bean
  @ConditionalOnProperty(name = "datasource.flyway.load", havingValue = "true", matchIfMissing = true)
  @Override
  public Flyway flyway() {
    Flyway flyWay =
        Flyway.configure()
            .dataSource(dataSource())
            .locations("classpath:/db/migration/pgsql")
            .load();
    flyWay.migrate();
    return flyWay;
  }

  @Bean
  @ConditionalOnProperty(name = "datasource.flyway.load", havingValue = "false", matchIfMissing = true)
  public Flyway flywayNoLoad() {
    Flyway flyWay =
        Flyway.configure()
            .dataSource(dataSource())
            .locations("classpath:/db/migration/pgsql")
            .load();
    return flyWay;
  }

  @Override
  public String getDbType() {
    return "pgsql";
  }

  @Bean
  KeyVault keyVault() {
    var privateKey = getPrivateKey();
    var publicKey = getPublicKey();

    if (privateKey.isEmpty() || publicKey.isEmpty()) {
      var kp = super.getKeyPair(algorithm);
      var gaenKp = new KeyVault.KeyVaultKeyPair("gaen", kp);
      var nextDayJWTKp = new KeyVault.KeyVaultKeyPair("nextDayJWT", kp);
      var hashFilterKp = new KeyVault.KeyVaultKeyPair("hashFilter", kp);
      return new KeyVault(gaenKp, nextDayJWTKp, hashFilterKp);
    }

    var gaen = new KeyVault.KeyVaultEntry("gaen", getPrivateKey(), getPublicKey(), "EC");
    var nextDayJWT =
        new KeyVault.KeyVaultEntry("nextDayJWT", getPrivateKey(), getPublicKey(), "EC");
    var hashFilter =
        new KeyVault.KeyVaultEntry("hashFilter", getPrivateKey(), getPublicKey(), "EC");

    try {
      return new KeyVault(gaen, nextDayJWT, hashFilter);
    } catch (PrivateKeyNoSuitableEncodingFoundException
        | PublicKeyNoSuitableEncodingFoundException e) {
      throw new RuntimeException(e);
    }
  }

  String getPrivateKey() {
    return new String(Base64.getDecoder().decode(privateKey));
  }

  String getPublicKey() {
    return new String(Base64.getDecoder().decode(publicKey));
  }

  @Profile("debug-sedia")
  @Configuration
  public static class DebugConfig {
    @Value("${ws.exposedlist.debug.releaseBucketDuration: 86400000}")
    long releaseBucketDuration;

    @Value("${ws.exposedlist.debug.requestTime: 1500}")
    long requestTime;

    @Autowired KeyVault keyVault;
    @Autowired Flyway flyway;
    @Autowired DataSource dataSource;
    @Autowired ProtoSignature gaenSigner;
    @Autowired ValidateRequest backupValidator;
    @Autowired ValidationUtils gaenValidationUtils;
    @Autowired Environment env;

    protected boolean isProd() {
      return Arrays.asList(env.getActiveProfiles()).contains("radarcovid-prod");
    }

    protected boolean isDev() {
      return Arrays.asList(env.getActiveProfiles()).contains("radarcovid-local");
    }

    @Bean
    DebugGAENDataService dataService() {
      String dbType = "";
      if (isProd()) {
        dbType = "pgsql";
      } else if (isDev()) {
        dbType = "hsqldb";
      }
      return new DebugJDBCGAENDataServiceImpl(dbType, dataSource);
    }

    @Bean
    public InsertManager insertManagerDebug() {
      var manager = InsertManager.getDebugInsertManager(dataService(), gaenValidationUtils);
      manager.addFilter(new AssertKeyFormat(gaenValidationUtils));
      manager.addFilter(new RemoveKeysFromFuture());
      manager.addFilter(new EnforceRetentionPeriod(gaenValidationUtils));
      manager.addFilter(new RemoveFakeKeys());
      manager.addFilter(new EnforceValidRollingPeriod());
      return manager;
    }

    @Bean
    DebugController debugController() {
      return new DebugController(
          dataService(),
          gaenSigner,
          backupValidator,
          insertManagerDebug(),
          Duration.ofMillis(releaseBucketDuration),
          Duration.ofMillis(requestTime));
    }
  }
}
