package de.gematik.isik.mockserver.provider;

import ca.uhn.fhir.jpa.api.dao.DaoRegistry;
import ca.uhn.fhir.jpa.api.dao.IFhirResourceDao;
import ca.uhn.fhir.jpa.searchparam.SearchParameterMap;
import ca.uhn.fhir.jpa.topic.SubscriptionTopicDispatcher;
import ca.uhn.fhir.rest.annotation.Operation;
import ca.uhn.fhir.rest.annotation.OperationParam;
import ca.uhn.fhir.rest.api.RestOperationTypeEnum;
import ca.uhn.fhir.rest.api.server.IBundleProvider;
import ca.uhn.fhir.rest.param.TokenParam;
import ca.uhn.fhir.rest.server.exceptions.InvalidRequestException;
import ca.uhn.fhir.rest.server.exceptions.PreconditionFailedException;
import ca.uhn.fhir.rest.server.exceptions.UnprocessableEntityException;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.BooleanType;
import org.hl7.fhir.r4.model.IdType;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.OperationOutcome;
import org.hl7.fhir.r4.model.OperationOutcome.IssueSeverity;
import org.hl7.fhir.r4.model.Parameters;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.Patient.LinkType;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.ResourceType;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * Implements the HL7 Patient {@code $merge} operation
 * (https://hl7.org/fhir/patient-operation-merge.html).
 *
 * <p>Note: the {@code replaces} link on the target patient is intentionally set as a
 * <em>logical</em> reference (via the source patient's MR identifier) rather than a literal
 * reference, because the ISiK specification permits the obsolete (source) patient to be deleted,
 * which would dangle a literal reference.
 */
@Service
public class PatientMergeOperationProvider {

	private static final String MERGE_TOPIC_CRITERIA = "https://gematik.de/fhir/isik/SubscriptionTopic/patient-merge";

	private final DaoRegistry daoRegistry;
	private final SubscriptionTopicDispatcher subscriptionTopicDispatcher;

	public PatientMergeOperationProvider(
			DaoRegistry daoRegistry, SubscriptionTopicDispatcher subscriptionTopicDispatcher) {
		this.daoRegistry = daoRegistry;
		this.subscriptionTopicDispatcher = subscriptionTopicDispatcher;
	}

	@Operation(name = "$merge", typeName = "Patient")
	public Parameters patientMerge(
			@OperationParam(name = "source-patient", min = 0, max = 1) Reference sourcePatientRef,
			@OperationParam(name = "source-patient-identifier", min = 0, max = OperationParam.MAX_UNLIMITED)
					List<Identifier> sourcePatientIdentifiers,
			@OperationParam(name = "target-patient", min = 0, max = 1) Reference targetPatientRef,
			@OperationParam(name = "target-patient-identifier", min = 0, max = OperationParam.MAX_UNLIMITED)
					List<Identifier> targetPatientIdentifiers,
			@OperationParam(name = "result-patient", min = 0, max = 1) Patient resultPatient,
			@OperationParam(name = "preview", min = 0, max = 1) BooleanType preview) {

		IFhirResourceDao patientDao = daoRegistry.getResourceDao(ResourceType.Patient.name());

		boolean previewOnly = preview != null && preview.booleanValue();

		Patient sourcePatient = resolvePatient(patientDao, sourcePatientRef, sourcePatientIdentifiers, "source");
		Patient targetPatient = resolvePatient(patientDao, targetPatientRef, targetPatientIdentifiers, "target");

		Reference targetReference =
				new Reference("Patient/" + targetPatient.getIdElement().getIdPart());

		// Source: mark inactive and link replaced-by -> target (literal reference)
		sourcePatient.setActive(false);
		sourcePatient.addLink().setType(LinkType.REPLACEDBY).setOther(targetReference);

		// Logical reference for the target's replaces link: the source patient's MR (PID).
		Optional<Identifier> pid = sourcePatient.getIdentifier().stream()
				.filter(i -> i.getType().getCoding().stream()
						.anyMatch(t -> t.getCode().equals("MR")))
				.findFirst();

		if (pid.isEmpty()) {
			throw new PreconditionFailedException("Patients need a populated PID (Identifier.type = MR)");
		}

		// If the caller supplied the expected final state, use it as the basis for the target.
		if (resultPatient != null) {
			resultPatient.setId(targetPatient.getIdElement().toUnqualifiedVersionless());
			targetPatient = resultPatient;
		}

		// Target: link replaces -> source (logical reference via the source MR identifier)
		targetPatient.addLink().setType(LinkType.REPLACES).getOther().setIdentifier(pid.get());

		OperationOutcome operationOutcome = new OperationOutcome();

		if (previewOnly) {
			operationOutcome
					.addIssue()
					.setSeverity(IssueSeverity.INFORMATION)
					.setDiagnostics("Preview - patient merge not persisted");
		} else {
			patientDao.update(sourcePatient);
			patientDao.update(targetPatient);

			subscriptionTopicDispatcher.dispatch(
					MERGE_TOPIC_CRITERIA, List.of(targetPatient), RestOperationTypeEnum.UPDATE);

			operationOutcome
					.addIssue()
					.setSeverity(IssueSeverity.INFORMATION)
					.setDiagnostics("Patient merge successful");
		}

		Parameters retVal = new Parameters();
		retVal.addParameter().setName("outcome").setResource(operationOutcome);
		retVal.addParameter().setName("result-patient").setResource(targetPatient);
		return retVal;
	}

	private Patient resolvePatient(
			IFhirResourceDao patientDao, Reference reference, List<Identifier> identifiers, String role) {

		boolean hasReference = reference != null && reference.hasReference();
		boolean hasIdentifiers = identifiers != null && !identifiers.isEmpty();

		if (hasReference == hasIdentifiers) {
			throw new InvalidRequestException(String.format(
					"Exactly one of '%s-patient' or '%s-patient-identifier' must be provided", role, role));
		}

		if (hasReference) {
			return (Patient) patientDao.read(new IdType(reference.getReference()));
		}

		SearchParameterMap searchMap = new SearchParameterMap();
		for (Identifier identifier : identifiers) {
			searchMap.add(Patient.SP_IDENTIFIER, new TokenParam(identifier.getSystem(), identifier.getValue()));
		}

		IBundleProvider results = patientDao.search(searchMap);
		List<IBaseResource> matches = results.getResources(0, 2);

		if (matches.size() != 1) {
			throw new UnprocessableEntityException(String.format(
					"%s-patient-identifier must resolve to exactly one Patient, but matched %d", role, matches.size()));
		}

		return (Patient) matches.get(0);
	}
}
