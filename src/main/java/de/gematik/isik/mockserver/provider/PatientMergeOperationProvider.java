package de.gematik.isik.mockserver.provider;

import ca.uhn.fhir.jpa.api.dao.DaoRegistry;
import ca.uhn.fhir.jpa.api.dao.IFhirResourceDao;
import ca.uhn.fhir.jpa.topic.SubscriptionTopicDispatcher;
import ca.uhn.fhir.rest.annotation.Operation;
import ca.uhn.fhir.rest.annotation.OperationParam;
import ca.uhn.fhir.rest.api.RestOperationTypeEnum;
import ca.uhn.fhir.rest.server.exceptions.PreconditionFailedException;
import org.hl7.fhir.r4.model.IdType;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.OperationOutcome;
import org.hl7.fhir.r4.model.OperationOutcome.IssueSeverity;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.Patient.LinkType;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.ResourceType;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

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

	@Operation(name = "$patient-merge")
	public OperationOutcome patientMerge(
			@OperationParam(name = "source-patient", min = 1, max = 1) Reference sourcePatientRef,
			@OperationParam(name = "target-patient", min = 1, max = 1) Reference targetPatientRef) {

		IFhirResourceDao patientDao = daoRegistry.getResourceDao(ResourceType.Patient.name());

		Patient sourcePatient = (Patient) patientDao.read(new IdType(sourcePatientRef.getReference()));
		Patient targetPatient = (Patient) patientDao.read(new IdType(targetPatientRef.getReference()));

		sourcePatient.setActive(false);
		sourcePatient.addLink().setType(LinkType.REPLACEDBY).setOther(targetPatientRef);

		Optional<Identifier> pid = sourcePatient.getIdentifier().stream()
				.filter(i -> i.getType().getCoding().stream()
						.anyMatch(t -> t.getCode().equals("MR")))
				.findFirst();

		if (pid.isEmpty()) {
			throw new PreconditionFailedException("Patients need a populated PID (Identifier.type = MR)");
		}

		targetPatient.addLink().setType(LinkType.REPLACES).getOther().setIdentifier(pid.get());

		patientDao.update(sourcePatient);
		patientDao.update(targetPatient);

		subscriptionTopicDispatcher.dispatch(
				MERGE_TOPIC_CRITERIA, List.of(targetPatient), RestOperationTypeEnum.UPDATE);

		OperationOutcome operationOutcome = new OperationOutcome();
		operationOutcome.addIssue().setSeverity(IssueSeverity.INFORMATION).setDiagnostics("Patient merge successful");
		return operationOutcome;
	}
}
