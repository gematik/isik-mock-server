<img align="right" width="250" height="47" alt="gematik GmbH" src="img/gematik_logo.png"/> <br/>

# ISiK Mock Server

<details>
  <summary>Table of Contents</summary>
  <ol>
    <li>
      <a href="#about-the-project">About The Project</a>
       <ul>
        <li><a href="#release-notes">Release Notes</a></li>
      </ul>
	</li>
    <li>
      <a href="#getting-started">Getting Started</a>
      <ul>
        <li><a href="#prerequisites">Prerequisites</a></li>
        <li><a href="#installation">Installation</a></li>
      </ul>
    </li>
    <li><a href="#usage">Usage</a></li>
    <li><a href="#contributing">Contributing</a></li>
    <li><a href="#license">License</a></li>
    <li><a href="#contact">Contact</a></li>
  </ol>
</details>

## About The Project

This is a simple implementation of ISiK Level 3 and 5 specifications to be used as a simulation for testing purposes. It
is based on the [HAPI FHIR Starter Project](https://github.com/hapifhir/hapi-fhir-jpaserver-starter).

### Release Notes

See [ReleaseNotes.md](./ReleaseNotes.md) for all information regarding the (newest) releases.

## Getting Started

### Prerequisites

- Java (JDK) installed: JDK 21 or newer.
- Apache Maven build tool (3.8+)
- Alternatively: Docker

### Installation

To build and run the mock server, you can use Docker or deploy the WAR file on a Apache Tomcat server.

#### Using Docker

You can run the server as a Docker Container with an in-memory Database by issuing the command:

```bash
docker run --rm -it -p 8080:8080 gematik1/isik-mock-server:latest
```

#### Using Docker Compose

You can run the server as a Docker Container with dedicated PostgreSQL Database by issuing the command:

```bash
docker compose up -d
```

The server will then be accessible at http://localhost:8080/fhir and eg. http://localhost:8080/fhir/metadata after some
seconds (usually 30-60).

## Usage

Once started the server is accessible at http://localhost:8080/fhir and the CapabilityStatement will be found
at http://localhost:8080/fhir/metadata.

Please refer to the main HAPI FHIR Jpa Server documentation for further information about the general server
functionality: [HAPI FHIR Jpa Server Documentation](https://github.com/hapifhir/hapi-fhir-jpaserver-starter/blob/master/README.md)

### ISiK-specific Features

#### General Design Decisions regarding ISiK

##### Non-acceptance of instances on CREATE that are not ISiK compliant

Although ISiK generally permits compatibility with non‑ISiK‑compliant instances for historical data (particularly when
returning data in a READ interaction), this server implementation does not adopt a liberal approach that would accept
such instances during a CREATE interaction. The server rejects CREATE requests if the supplied resources are not
conformant with an existing ISiK profile.

##### Instances that are not ISiK compliant

This ISiK server may persist instances that are not ISiK compliant. These emulate legacy or historical data which a
client should still be able to process in a READ interaction using appropriate error handling.

#### FHIR Resource Validation with the `gematik Referenzvalidator`

Every FHIR resource that is being sent to the server via a `POST` or `PUT` request is being validated with
the [gematik Referenzvalidator](https://github.com/gematik/app-referencevalidator) using
the [ISIK-3 Plugins](https://github.com/gematik/app-referencevalidator-plugins?tab=readme-ov-file#isik3). If a resource
is not valid it gets rejected and a response with an OperationOutcome containing the validation errors is being sent
back to the client. Only resources that are valid will be accepted.

#### Accepted Media-Types

The server accepts XML and JSON. Clients can choose between XML and JSON representation, but must indicate which
representation has been selected in the HTTP Accept and Content-Type
headers ([cf. ISiK v3 Specification](https://simplifier.net/guide/isik-basis-v3/UebergreifendeFestlegungen-Repraesentationsformate?version=current), [cf. ISiK v5 Specification](https://simplifier.net/guide/isik-basis-stufe-5/Einfuehrung/Festlegungen/UebergreifendeFestlegungen_Repraesentationsformate?version=current)).

#### Booking Appointments - `Appointment/$book`

The server implements the Operation for booking Appointments according to the
official [ISiK v5 Specification](https://simplifier.net/guide/isik-terminplanung-stufe-5/Einfuehrung?version=5.1.1)
or [ISiK v3 Specification](https://simplifier.net/guide/isik-terminplanung-v3/ImplementationGuide-markdown-Datenobjekte-Operations?version=current),
according to the Profile of the Resources.

Plausibility checks:

* Start date of the appointment must be in the future
* Status must be `proposed`
* serviceType must use the CodeSystem `http://terminology.hl7.org/CodeSystem/service-type`
* The referenced slot must have the status `free`
* The referenced patient must not have been deleted and must not have the status active=false
* The start and end times in the appointment must be identical to the slot start/end times or lie between them

Also, the server automatically creates a busy Slot when none is provided — ensuring no overlaps and converting
overlapping free slots — then associates it with the new appointment.

#### Asynchronous Booking of Appointments

Clients can also book Appointments asynchronously. To do this they need to add a `Prefer-Header` that contains
`respond-async`. The server will then send a response with status `202` and a `Content-Location-Header` that contains
the url where the client can later access the result of the asynchronous booking job. To access the result of the
asynchronous job the client needs to do a `GET` request with the url from the `Content-Location-Header`.

#### Updating Appointments

The server implements updating Appointments according to the chapter `Aktualisierung / Absage eines Termins` from the
official [ISiK v5 Specification](https://simplifier.net/guide/isik-terminplanung-stufe-5/Einfuehrung/Festlegungen/Operations?version=5.1.1)
or [ISiK v3 Specification](https://simplifier.net/guide/isik-terminplanung-v3/ImplementationGuide-markdown-Datenobjekte-Operations?version=current).

Plausibility checks:

* `Appointment.slot` MUST NOT be changed
* `Appointment.start` MUST NOT be changed
* `Appointment.end` MUST NOT be changed
* `Appointment.participant.actor.where(resolve() is Patient)` MUST NOT be changed
* The referenced patient MUST NOT be deleted and MUST NOT have the status `active=false`

#### Rescheduling Appointments

The server implements rescheduling of Appointments as described in the `cancelled-appt-id` parameter in the
official [ISiK v5 Specification](https://simplifier.net/guide/isik-terminplanung-stufe-5/Einfuehrung/Festlegungen/Operations?version=5.1.1) or [ISiK v3 Specification](https://simplifier.net/guide/isik-terminplanung-v3/ImplementationGuide-markdown-Datenobjekte-Operations?version=current).

* All plausibility checks from the chapter `Booking Appointments` need to be fulfilled here as well.
* A reference to the cancelled appointment is stored in the new appointment.
* The status of the cancelled appointment is set to `cancelled`.

#### DocumentReferences: Updating Documents

The server adds a `relatesTo relation` to the previous document. The status of the previous document is set to
`superseded` by the server.

#### DocumentReferences: KDL Code Mapping

The server completes any missing `XDS` class and type codes using the transmitted `KDL` code and returns them in
`DocumentReference.type` or `DocumentReference.category`. The `XDS` codes determined from the `KDL` code using the
ConceptMaps published as part of the [KDL specification](https://simplifier.net/kdl). The `XDS` codes are required for
cross-institutional document exchange via `IHE XDS` or `MHD` or for the transmission of documents to the patient's
`ePA`.

#### DocumentReferences: Generating Metadata - `DocumentReference/$generate-metadata`

The server supports the Operation of generating of metadata as described in the
official [ISiK v5 Specification](https://simplifier.net/guide/isik-dokumentenaustausch-stufe-5/Einfuehrung/Festlegungen/ErzeugenVonMetadaten?version=5.1.1) or [ISiK v3 Specification](https://simplifier.net/guide/isik-dokumentenaustausch-v3/ImplementationGuide-markdown-AkteureUndInteraktionen-ErzeugenVonMetadaten?version=current).

> Warning
>
> Although `meta.profile` is not actually mandatory for ISIK-FHIR resources, the specification of the profile
`“https://gematik.de/fhir/isik/v3/Basismodul/StructureDefinition/ISiKBerichtBundle”` in `meta.profile` is currently
> mandatory for the correct creation of metadata. It is also mandatory to specify `meta.profile` in such a bundle for
> the
> composition: `"https://gematik.de/fhir/isik/v3/Basismodul/StructureDefinition/ISiKBerichtSubSysteme"`.

#### DocumentReferences: Updating Metadata - `DocumentReference/$update-metadata`

The server supports the Operation of generating of metadata as described in the
official [ISiK v5 Specification](https://simplifier.net/guide/isik-dokumentenaustausch-stufe-5/Einfuehrung/Festlegungen/Update?version=5.1.1) or [ISIK-3 specification](https://simplifier.net/guide/isik-dokumentenaustausch-v3/ImplementationGuide-markdown-AkteureUndInteraktionen-Update?version=current).

#### DocumentReferences: Resource Validation on Server Start

The `example-fhir-resources.validation.enabled` flag controls whether example FHIR resources are validated during server
startup. Validation ensures resource integrity but significantly slows down the server's startup. By default, it is
disabled (set in `application.yml`) to speedup development. If you want to validate the example resources on startup,
enable the flag by adding the following VM option to your runtime configuration:

```
-Dexample-fhir-resources.validation.enabled=true
```

- **Enabled:** Average startup time on local dev machine ~58 s
- **Disabled:** Average startup time on local dev machine ~19 s

## Contributing

If you want to contribute, please check our [CONTRIBUTING.md](./CONTRIBUTING.md).

## License

Copyright 2025-2026 gematik GmbH

Apache License, Version 2.0

See the [LICENSE](./LICENSE) for the specific language governing permissions and limitations under the License

## Additional Notes and Disclaimer from gematik GmbH

1. Copyright notice: Each published work result is accompanied by an explicit statement of the license conditions for
   use. These are regularly typical conditions in connection with open source or free software. Programs
   described/provided/linked here are free software, unless otherwise stated.
2. Permission notice: Permission is hereby granted, free of charge, to any person obtaining a copy of this software and
   associated documentation files (the "Software"), to deal in the Software without restriction, including without
   limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the
   Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:
    1. The copyright notice (Item 1) and the permission notice (Item 2) shall be included in all copies or substantial
       portions of the Software.
    2. The software is provided "as is" without warranty of any kind, either express or implied, including, but not
       limited to, the warranties of fitness for a particular purpose, merchantability, and/or non-infringement. The
       authors or copyright holders shall not be liable in any manner whatsoever for any damages or other claims arising
       from, out of or in connection with the software or the use or other dealings with the software, whether in an
       action of contract, tort, or otherwise.
    3. The software is the result of research and development activities, therefore not necessarily quality assured and
       without the character of a liable product. For this reason, gematik does not provide any support or other user
       assistance (unless otherwise stated in individual cases and without justification of a legal obligation).
       Furthermore, there is no claim to further development and adaptation of the results to a more current state of
       the art.
3. Gematik may remove published results temporarily or permanently from the place of publication at any time without
   prior notice or justification.
4. Please note: Parts of this code may have been generated using AI-supported technology. Please take this into account,
   especially when troubleshooting, for security analyses and possible adjustments.

## Contact

We take open source license compliance very seriously. We are always striving to achieve compliance at all times and to
improve our processes.
This software is currently being tested to ensure its technical quality and legal compliance. Your feedback is highly
valued.
If you find any issues or have any suggestions or comments, or if you see any other ways in which we can improve, please
open a GitHub issue or a ticket within [Anfrageportal ISiK](https://service.gematik.de/servicedesk/customer/portal/16).
