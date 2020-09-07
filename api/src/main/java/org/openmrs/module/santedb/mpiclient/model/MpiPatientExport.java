package org.openmrs.module.santedb.mpiclient.model;

import org.openmrs.*;
import org.openmrs.api.ObsService;
import org.openmrs.api.context.Context;
import org.openmrs.module.santedb.mpiclient.configuration.MpiClientConfiguration;
import org.openmrs.parameter.EncounterSearchCriteria;
import org.openmrs.parameter.EncounterSearchCriteriaBuilder;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * Encapsulates the data that may be collected and exported to the CR
 */
public class MpiPatientExport implements Serializable {
    private Patient patient;
    private List<Relationship> relationships;
    private Location birthPlace;
    private PersonAttribute mothersMaidenName;
    private Set<Obs> patientObs;

    public MpiPatientExport(Patient patient, List<Relationship> relationships, Location birthPlace, PersonAttribute mothersMaidenName, Set<Obs> patientObs) {
        this.patient = patient;
        this.relationships = relationships;
        this.birthPlace = birthPlace;
        this.mothersMaidenName = mothersMaidenName;
        this.patientObs = patientObs;
    }

    // Get health information exchange information
    private MpiClientConfiguration mConfiguration = MpiClientConfiguration.getInstance();

    public Patient getPatient() {
        return patient;
    }

    public void setPatient(Patient patient) {
        this.patient = patient;
    }

    public List<Relationship> getRelationships() {
        return relationships;
    }

    public void setRelationships(List<Relationship> relationships) {
        this.relationships = relationships;
    }

    public Location getBirthPlace() {
        if (this.birthPlace != null) {
            return this.birthPlace;
        } else {
            ObsService obsService = Context.getObsService();
            Concept registrationConcept = Context.getConceptService().getConceptByUuid(this.mConfiguration.getRegistrationConceptUuid());
            return obsService.getObservationsByPersonAndConcept(patient, registrationConcept).get(0).getLocation();
        }
    }

    public void setBirthPlace(Location birthPlace) {
        this.birthPlace = birthPlace;
    }

    public PersonAttribute getMothersMaidenName() {
        if (mothersMaidenName != null) {
            return mothersMaidenName;
        } else {
            return patient.getAttribute(this.mConfiguration.getMothersAttributeName());
        }
    }

    public void setMothersMaidenName(PersonAttribute mothersMaidenName) {
        this.mothersMaidenName = mothersMaidenName;
    }

    public Set<Obs> getPatientObs() {
        if (patientObs != null && patientObs.size() > 0) {
            return patientObs;
        } else {
            EncounterType registrationEncounterType = Context.getEncounterService().getEncounterTypeByUuid(this.mConfiguration.getRegistrationEncounterUuid());
            Collection<EncounterType> encounterTypeList = new ArrayList<EncounterType>() {{
                add(registrationEncounterType);
            }};
            EncounterSearchCriteriaBuilder builder = new EncounterSearchCriteriaBuilder();
            EncounterSearchCriteria encounterSearchCriteria = builder.setPatient(patient).setEncounterTypes(encounterTypeList).createEncounterSearchCriteria();
            Encounter registrationEncounter = Context.getEncounterService().getEncounters(encounterSearchCriteria).get(0);
            return registrationEncounter.getObsAtTopLevel(false);
        }

    }

    public void setPatientObs(Set<Obs> patientObs) {
        this.patientObs = patientObs;
    }
}
