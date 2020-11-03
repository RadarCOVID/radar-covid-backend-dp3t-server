/*
 * Copyright (c) 2020 Gobierno de España
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
import org.dpppt.backend.sdk.data.gaen.DebugGAENDataService;
import org.dpppt.backend.sdk.data.gaen.DebugJDBCGAENDataServiceImpl;
import org.dpppt.backend.sdk.ws.controller.DebugController;
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

import javax.sql.DataSource;
import java.time.Duration;
import java.util.Arrays;
import java.util.Base64;
import java.util.Properties;

@Configuration
@Profile({"radarcovid-local","radarcovid-pre","radarcovid-pro"})
public class WSSediaConfig extends WSBaseConfig {
	
	@Value("${datasource.username}")
	String dataSourceUser;

	@Value("${datasource.password}")
	String dataSourcePassword;

	@Value("${datasource.url}")
	String dataSourceUrl;

	@Value("${datasource.reader-url}")
	String dataSourceReaderUrl;

	@Value("${datasource.schema}")
	String dataSourceSchema;
	
	@Value("${datasource.driverClassName}")
	String dataSourceDriver;

	@Value("${datasource.failFast}")
	String dataSourceFailFast;

	@Value("${datasource.minimumIdle:5}")
	int datasSourceMinimumIdle;

	@Value("${datasource.maximumPoolSize:20}")
	int dataSourceMaximumPoolSize;

	@Value("${datasource.maxLifetime}")
	String dataSourceMaxLifetime;

	@Value("${datasource.idleTimeout}")
	String dataSourceIdleTimeout;

	@Value("${datasource.connectionTimeout}")
	String dataSourceConnectionTimeout;

	@Value("${ws.ecdsa.credentials.privateKey:}")
	private String privateKey;
	
	@Value("${ws.ecdsa.credentials.publicKey:}")
    public String publicKey;

	@Override
	@Bean(destroyMethod = "close")
	public DataSource dataSource() {
		return connectionPoolDataSource(dataSourceUrl);
	}

//	//@Bean(destroyMethod = "close")
//	public DataSource readWriteDataSource() {
//		return connectionPoolDataSource(dataSourceUrl);
//	}
//
//	//@Bean(destroyMethod = "close")
//	public DataSource readOnlyDataSource() {
//		return connectionPoolDataSource(dataSourceReaderUrl);
//	}
//
//	@Bean
//	public DataSource dataSource() {
//		return new MasterReplicaRoutingDataSource(readWriteDataSource(), readOnlyDataSource());
//	}

	protected HikariConfig hikariConfig(String url) {
		Properties props = new Properties();
		props.put("url", url);
		props.put("user", dataSourceUser);
		props.put("password", dataSourcePassword);
		HikariConfig config = new HikariConfig();
		config.setDataSourceProperties(props);
		config.setDataSourceClassName(dataSourceDriver);
		config.setSchema(dataSourceSchema);
		config.setMinimumIdle(datasSourceMinimumIdle);
		config.setMaximumPoolSize(dataSourceMaximumPoolSize);
		config.setMaxLifetime(Integer.parseInt(dataSourceMaxLifetime));
		config.setIdleTimeout(Integer.parseInt(dataSourceIdleTimeout));
		config.setConnectionTimeout(Integer.parseInt(dataSourceConnectionTimeout));
		return config;
	}

	protected HikariDataSource connectionPoolDataSource(String url) {
		return new HikariDataSource(hikariConfig(url));
	}

	@Bean
	@Override
	@ConditionalOnProperty(name = "datasource.flyway.load", havingValue = "true", matchIfMissing = true)
	public Flyway flyway() {
		Flyway flyWay = Flyway.configure().dataSource(dataSource()).locations("classpath:/db/migration/pgsql").load();
		flyWay.migrate();
		return flyWay;
	}

	@Bean
	@ConditionalOnProperty(name = "datasource.flyway.load", havingValue = "false", matchIfMissing = true)
	public Flyway flywayNoLoad() {
		Flyway flyWay = Flyway.configure().dataSource(dataSource()).locations("classpath:/db/migration/pgsql").load();
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
		
		if(privateKey.isEmpty() || publicKey.isEmpty()) {
			var kp = super.getKeyPair(algorithm);
			var gaenKp = new KeyVault.KeyVaultKeyPair("gaen", kp);
			var nextDayJWTKp = new KeyVault.KeyVaultKeyPair("nextDayJWT", kp);
			var hashFilterKp = new KeyVault.KeyVaultKeyPair("hashFilter", kp);
			return new KeyVault(gaenKp, nextDayJWTKp, hashFilterKp);
		}

		var gaen = new KeyVault.KeyVaultEntry("gaen", getPrivateKey(), getPublicKey(), "EC");
		var nextDayJWT = new KeyVault.KeyVaultEntry("nextDayJWT", getPrivateKey(), getPublicKey(), "EC");
		var hashFilter = new KeyVault.KeyVaultEntry("hashFilter", getPrivateKey(), getPublicKey(), "EC"); 

		try {
			return new KeyVault(gaen, nextDayJWT, hashFilter);
		} catch (PrivateKeyNoSuitableEncodingFoundException | PublicKeyNoSuitableEncodingFoundException e) {
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
		@Value("${ws.exposedlist.debug.batchlength: 86400000}")
		long batchLength;
	
		@Value("${ws.exposedlist.debug.requestTime: 1500}")
		long requestTime;

		@Autowired
		KeyVault keyVault;
		@Autowired
		Flyway flyway;
		@Autowired
		DataSource dataSource;
		@Autowired
		ProtoSignature gaenSigner;
		@Autowired
		ValidateRequest backupValidator;
		@Autowired
		ValidationUtils gaenValidationUtils;
		@Autowired
		Environment env;
		
		protected boolean isProd() {
			return !Arrays.asList(env.getActiveProfiles()).contains("radarcovid-local");
		}
		protected boolean isDev() {
			return Arrays.asList(env.getActiveProfiles()).contains("radarcovid-local");
		}
		
		@Bean
			DebugGAENDataService dataService() {
				String dbType = "";
				if(isProd()) {
					dbType = "pgsql";
				} else if(isDev()) {
					dbType = "hsqldb";
				}
			return new DebugJDBCGAENDataServiceImpl(dbType, dataSource);
		}
		@Bean
		DebugController debugController() {
			return new DebugController(dataService(),gaenSigner,backupValidator, gaenValidationUtils,Duration.ofMillis(batchLength), Duration.ofMillis(requestTime));
		}
	}

}
