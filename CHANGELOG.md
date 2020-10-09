# Changelog

All notable changes to this project will be documented in this file.

## [Unreleased]

### Added

- Setted responses retention time on fake requests
- Added aspect configuration

### Fixed

- Fixed messages in aspects.

## [1.1.2.RELEASE] - 2020-10-09

### Added

- Updated DP3T release: 1.1.2
- Environment description in README.md file.
- Added license
- Added CODE_OF_CONDUCT.md file.
- Added CHANGELOG.md file.

### Changed

- Max keys are now 30.
- Updated TestContainers version

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
