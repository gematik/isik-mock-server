<img align="right" width="250" height="47" alt="gematik GmbH" src="img/gematik_logo.png"/> <br/>

# Release Notes ISiK Mock Server

## Release 3.4.0 (2026-01)

### added

* Custom CapabilityStatement Interceptor to provide the search parameter `_count` for all the known FHIR Resources

## Release 3.3.0 (2026-01)

### added

* New Plugin `isik5` version 1.0.0 for the support of ISiK 5 specification.

### changed

* Mapping of Profiles and Plugins have been extended for ISiK 5 support.
* Bumped Version of ReferenzValidator Plugin to 2.13.0
* For Resources without an explicit Profile, ISiK 5 Profiles are evaluated first, while ISiK 3 Profiles are used as fallback
* Updated references in the README to use the DockerHub Image `gematik/isik-mock-server`

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
