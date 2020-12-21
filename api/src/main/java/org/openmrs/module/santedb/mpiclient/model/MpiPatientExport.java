package org.openmrs.module.santedb.mpiclient.model;

import org.openmrs.*;
import org.openmrs.api.ObsService;
import org.openmrs.api.context.Context;
import org.openmrs.module.santedb.mpiclient.configuration.MpiClientConfiguration;
import org.openmrs.parameter.EncounterSearchCriteria;
import org.openmrs.parameter.EncounterSearchCriteriaBuilder;

import java.io.Serializable;
import java.util.*;

/**
 * Encapsulates the data that may be collected and exported to the CR
 */
public class MpiPatientExport implements Serializable {
    private Patient patient;
    private List<Relationship> relationships;
    private Location birthPlace;
    private PersonAttribute mothersMaidenName;
    private PersonAttribute patientTelephone;
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
            Concept registrationConcept = Context.getConceptService().getConceptByUuid(this.mConfiguration.getBirthPlaceConceptUuid());
            Obs birthPlaceObs = obsService.getObservationsByPersonAndConcept(patient, registrationConcept).get(0);
            if (birthPlaceObs != null) {
                return birthPlaceObs.getLocation();
            } else {
                return null;
            }

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

    public PersonAttribute getPatientTelephone() {
        if (patientTelephone != null) {
            return patientTelephone;
        } else {
            return patient.getAttribute(this.mConfiguration.getPatientTelephoneAttribute());
        }
    }

    public void setMothersMaidenName(PersonAttribute mothersMaidenName) {
        this.mothersMaidenName = mothersMaidenName;
    }

    public Set<Obs> getPatientObs() {
        if (patientObs != null && patientObs.size() > 0) {
            return patientObs;
        } else {
            return new HashSet<>();
        }

    }

    public void setPatientObs(Set<Obs> patientObs) {
        this.patientObs = patientObs;
    }
}
