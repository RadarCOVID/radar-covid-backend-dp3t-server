# Changelog

All notable changes to this project will be documented in this file.

## [Unreleased]

### Added

### Changed

### Fixed

## [2.0.1.RELEASE] - 2020-12-17

### Added

- Index in table t_gaen_exposed.
- Added Micrometer connecting with CloudWatch.
- Added minimum idle database connection.
- Added [efficient Docker images with Spring Boot 2.3](https://spring.io/blog/2020/08/14/creating-efficient-docker-images-with-spring-boot-2-3).
- Added origin countries and visited countries filters on gaen exposed retrieval.
- Added produces application/zip in GaenV2Controller (and raised a PR to DP3T).

### Changed

- Changed PostgreSQL Docker image from postgres:12 to postgres:12-alpine in testing.
- In Docker container, changed JRE 11 from HotSpot to OpenJ9 and applied more memory (from 1GB to 1.5GB).
- Changed to 2.0.1 DP3T Backend release.

### Fixed

## [1.1.2.RELEASE] - 2020-10-29

### Added

- Setted responses retention time on fake requests.
- Added aspect configuration.
- AOP aspects ordering.
- [ShedLock](https://github.com/lukas-krecan/ShedLock) support. [PR #262](https://github.com/DP-3T/dp3t-sdk-backend/pull/262) created on [dp3t-sdk-backend](https://github.com/DP-3T/dp3t-sdk-backend).
- Added validation trace. 
- [EU Federation Gateway Service (EFGS)](https://github.com/eu-federation-gateway-service/efgs-federation-gateway) integration using "_One World_" pattern. If EFGS sharing, service uses the new claim for it.

### Changed

- Log in debug mode fake tokens validation.

### Fixed

- Fixed messages in aspects.

## [1.1.2.RELEASE] - 2020-10-09

### Added

- Updated DP3T release: 1.1.2
- Environment description in README.md file.
- Added license.
- Added CODE_OF_CONDUCT.md file.
- Added CHANGELOG.md file.

### Changed

- Max keys are now 30.
- Updated TestContainers version.

### Deleted

- Removed a comment related a TODO that is not required to review.
- Deleted Headers started with "x-forwarded", in server logs.

### Fixed

- If JWT "alg" is "none", skip signature.
- Fixed contact email in THIRD-PARTY-NOTICES file.
- Fake tokens are not validated.
- Apps are sending fake TEKs for more days than expected.

## [1.0.5.RELEASE] - 2020-09-09

* DP3T Service. Initial version.

[Unreleased]: https://github.com/RadarCOVID/radar-covid-backend-dp3t-server/compare/1.1.2.RELEASE...develop
[1.1.2.RELEASE]: https://github.com/RadarCOVID/radar-covid-backend-dp3t-server/compare/1.0.5.RELEASE...1.1.2.RELEASE
[1.0.5.RELEASE]: https://github.com/RadarCOVID/radar-covid-backend-dp3t-server/releases/tag/1.0.5.RELEASE
