package org.openmrs.module.santedb.mpiclient.util;

import org.apache.commons.lang3.StringUtils;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.Reference;
import org.openmrs.PatientIdentifier;
import org.openmrs.PatientIdentifierType;
import org.openmrs.api.context.Context;
import org.openmrs.module.fhir2.api.FhirPatientService;
import org.openmrs.module.fhir2.api.dao.FhirLocationDao;
import org.openmrs.module.fhir2.api.dao.impl.FhirLocationDaoImpl;
import org.openmrs.module.fhir2.api.impl.FhirPatientServiceImpl;

import java.util.Optional;

public class IdentifierTranslator {

    public static PatientIdentifier translateIdentifier(Identifier identifier) {
        if (identifier == null) {
            return null;
        }
        return toOpenmrsType(new PatientIdentifier(), identifier);
    }

    public static PatientIdentifier toOpenmrsType(PatientIdentifier patientIdentifier, Identifier identifier) {
        FhirPatientService patientService =  new FhirPatientServiceImpl();
        FhirLocationDao locationDao = new FhirLocationDaoImpl();


        if (patientIdentifier == null || identifier == null) {
            return patientIdentifier;
        }

        patientIdentifier.setUuid(identifier.getId());

        patientIdentifier.setIdentifier(identifier.getValue());

        patientIdentifier.setPreferred(Identifier.IdentifierUse.OFFICIAL.equals(identifier.getUse()));

        PatientIdentifierType type = getPatientIdentifierTypeByIdentifier(identifier);

        if (type == null && patientIdentifier.getIdentifierType() == null) {
            return null;
        }

        patientIdentifier.setIdentifierType(type);

        return patientIdentifier;
    }

    protected static  Optional<String> getReferenceId(Reference reference) {
        return referenceToId(reference.getReference());
    }

    private static Optional<String> referenceToId(String fhirReference) {
        if (fhirReference == null) {
            return Optional.empty();
        }

        int split = fhirReference.indexOf('/');
        if (split < 0 || split == fhirReference.length() - 1) {
            return Optional.empty();
        }

        return Optional.ofNullable(StringUtils.trimToNull(fhirReference.substring(split + 1)));
    }

    public static PatientIdentifierType getPatientIdentifierTypeByIdentifier(Identifier identifier) {
        if (identifier.getType() == null || StringUtils.isBlank(identifier.getType().getText())) {
            return null;
        }
        return Context.getPatientService().getPatientIdentifierTypeByName(identifier.getType().getText());
    }
}
