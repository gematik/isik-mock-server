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

	/**
	 * Handles the {@code Patient/$merge} operation: merges a source patient into a target patient.
	 *
	 * <p>Each patient is resolved either from its direct reference or from its identifiers (exactly
	 * one of the two per patient). The source is then deactivated and gets a {@code replaced-by} link
	 * to the target, while the target gets a {@code replaces} link back to the source as a logical
	 * reference (the source's MR identifier — see the class Javadoc for the rationale). Unless {@code
	 * preview} is {@code true}, both patients are persisted and a topic notification is dispatched
	 * for the merge.
	 *
	 * @param sourcePatientRef direct reference to the source patient; mutually exclusive with {@code
	 *     sourcePatientIdentifiers}
	 * @param sourcePatientIdentifiers identifiers resolving to exactly one source patient; mutually
	 *     exclusive with {@code sourcePatientRef}
	 * @param targetPatientRef direct reference to the surviving target patient; mutually exclusive
	 *     with {@code targetPatientIdentifiers}
	 * @param targetPatientIdentifiers identifiers resolving to exactly one target patient; mutually
	 *     exclusive with {@code targetPatientRef}
	 * @param resultPatient optional expected final state of the target patient; when present it
	 *     replaces the target's content before the links are applied
	 * @param preview when {@code true}, validate and compute the merge without persisting changes or
	 *     sending a notification
	 * @return a {@code Parameters} resource with an {@code outcome} ({@link OperationOutcome}) and
	 *     the merged {@code result-patient}
	 * @throws InvalidRequestException if neither or both of reference and identifiers are given for a
	 *     patient
	 * @throws UnprocessableEntityException if a patient's identifiers do not resolve to exactly one
	 *     patient
	 * @throws PreconditionFailedException if the source patient has no MR (PID) identifier
	 */
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

	/**
	 * Resolves a single patient either from a direct reference or from a set of identifiers,
	 * enforcing that exactly one of the two is supplied.
	 *
	 * @param patientDao the Patient DAO used to read or search
	 * @param reference direct reference to the patient, or {@code null}
	 * @param identifiers identifiers to search by (combined with AND), or {@code null}/empty
	 * @param role {@code "source"} or {@code "target"}, used only for error messages
	 * @return the resolved patient
	 * @throws InvalidRequestException if neither or both of {@code reference} and {@code identifiers}
	 *     are provided
	 * @throws UnprocessableEntityException if the identifiers match anything other than exactly one
	 *     patient
	 */
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
