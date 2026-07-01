<img align="right" width="250" height="47" alt="gematik GmbH" src="img/gematik_logo.png"/> <br/>

# Release Notes ISiK Mock Server

## Release 5.1.0 (2026-07)

### added

- Support for `Patient/$merge` Operation and Subscription Notifications

### changed

- Upgraded dependencies
- Renamed Docker Compose service names

### fixed

- Fixed mount point in the `docker-compose.yaml` file, preventing the start of the PostgreSQL Container

## Release 5.0.9 (2026-06)

### changed

- Upgraded dependencies

## Release 5.0.8 (2026-06)

### changed

- Upgraded dependencies

### fixed

- Fixed the missing Search Parameter `onset` for the Profile `ISiKAllergieUnvertraeglichkeit`

## Release 5.0.7 (2026-05)

### changed

- Upgraded dependencies

## Release 5.0.6 (2026-05)

### fixed

- Bugfix for the POST-based Search Queries

## Release 5.0.5 (2026-05)

### added

- New ISIK5 Plugin 1.0.2 (Spec Version 5.1.2), fixing the validation for `ISiKLebensZustand`-derived Profiles

### changed

- Upgraded HAPI FHIR to 8.8.1
- Updated Docker Base Image to latest version 1.0.12

### fixed

- Logic in the interceptor, preventing the execution of Search Queries with the POST HTTP Method

## Release 5.0.4 (2026-04)

### fixed

- Corrected supported ISiK 5 Profiles for Module **Basis**

## Release 5.0.3 (2026-04)

### fixed

- Corrected supported ISiK 5 Profiles for Module **Vitalparameter**

## Release 5.0.2 (2026-04)

### changed

- Updated Docker Base Image to latest version 1.0.10

## Release 5.0.1 (2026-03)

### fixed

- DocumentUpdateMetadata: when `docStatus` is `final`, it cannot be changed into `entered-in-error`
- KDL 2025 ValueSet invalid code issues are enforced as errors, preventing persistence of resources with invalid codes
- Added missing [THIRD_PARTY.txt](./THIRD-PARTY.txt) file

### changed

- Updated Docker Base Image to latest version 1.0.9

## Release 5.0.0 (2026-03)

### added

- Full Support for ISiK Level 5 Specification
- New ISIK 5 Example Resources
- Support for `TransactionBundle`
- Support for Asynchronous Appointment Booking
- Core FHIR validation fallback for unmapped resource types
- Bundle type-aware profile validation (transaction vs. document vs. searchset)
- `Appointment` PATCH: immutable field operation-type validation
- Slots are automatically freed when an Appointment is canceled
- Many security measures (path traversal prevention, leak of exception details, body limits, etc.)

### changed

- Performance Improvements
- Extended Search Parameters
- KDL 2025 ValueSet invalid code issues downgraded to warnings

### removed

- Support for ISiK Level 3, including the validation of ISiK 3 Profiles and the related example resources

## Release 3.4.5 (2026-03)

> [!WARNING]
> ISiK Level 3 support in this Mock Server Implementation has reached the End-of-Life and will be removed with the next
> major release.
> If you want to test further against this Mock Server for ISiK Level 3, please use the latest 3.4.5 Version.

### changed

- Added support for `RiskAssessment` Resource in ISiK 5 Profiles for Module **Medikation**

## Release 3.4.4 (2026-03)

> [!WARNING]
> ISiK Level 3 support in this Mock Server Implementation has reached the End-of-Life and will be removed with the next
> major release.
> If you want to test further against this Mock Server for ISiK Level 3, please use the latest 3.4.4 Version.

### changed

- Upgraded HAPI FHIR to 8.8.0
- Updated README with the deprecation notice for ISiK 3 support

## Release 3.4.3 (2026-02)

### added

- New Plugin `isik5` version 1.0.1 for the support of ISiK 5 specification.

### changed

- Upgraded HAPI FHIR to 8.6.5
- Updated Docker Base Image

### fixed

- Reintroduced Web UI (see: https://github.com/hapifhir/hapi-fhir-jpaserver-starter/pull/907)

## Release 3.4.2 (2026-02)

### changed

* Upgraded HAPI FHIR to 8.6.1

### fixed

* Removed `Communication` (ISIKNachricht) Profile from the list of supported Resources for ISiK 5

## Release 3.4.1 (2026-01)

### changed

* Extended support for ISiK 5 Profiles for the validation of `Location` and `Organization` resources
* Soft-failing validation in case of unknown ISiK 3 Profiles (e.g. `Location` and `Organization`)

## Release 3.4.0 (2026-01)

### added

* Custom CapabilityStatement Interceptor to provide the search parameter `_count` for all the known FHIR Resources

## Release 3.3.0 (2026-01)

### added

* New Plugin `isik5` version 1.0.0 for the support of ISiK 5 specification.

### changed

* Mapping of Profiles and Plugins have been extended for ISiK 5 support.
* Bumped Version of ReferenzValidator Plugin to 2.13.0
* For Resources without an explicit Profile, ISiK 5 Profiles are evaluated first, while ISiK 3 Profiles are used as
  fallback
* Updated references in the README to use the DockerHub Image `gematik1/isik-mock-server`

## Release 3.2.0 (2026-01)

### added

* New Property `example-fhir-resources.directory` to configure external FHIR Resources folder to be loaded on start

### changed

* Extended ResourceLoader class to load FHIR Resources from external folders
* Updated FHIR Examples and folder structure for tests
* Updated Docker Base Image
* Cleanup for GitHub Publication
* Bumped dependencies

## Release 3.1.0 (2025-12)

### changed

* Upgraded HAPI FHIR to 8.6.0
* Upgraded docker compose file to use PostgreSQL 16
* Updated ISiK Plugins:
    - basisprofil 1.1.1
    - dokumentenaustausch 2.1.0
    - medikation 1.1.0
    - terminplanung 1.2.0
    - vitalparameter 1.1.0

## Release 3.0.0 (2025-12)

### changed

* Renamed Artifact and Docker Image to "isik-mock-server"
* Updated ISiK Plugins:
    - basisprofil 1.1.0
    - dokumentenaustausch 2.0.1
    - medikation 1.0.1
    - terminplanung 1.1.1
    - vitalparameter 1.0.1

## Release 2.0.1 (2025-11)

### fixed

* Bugfix for running the server with the in-memory Database

## Release 2.0.0 (2025-10)

### changed

* Upgraded HAPI FHIR to 8.4.0
* Validation with invalid codes from KDL returns now a Warning message

## Release 1.0.0 (2025-07)

### added

* Initial release.
