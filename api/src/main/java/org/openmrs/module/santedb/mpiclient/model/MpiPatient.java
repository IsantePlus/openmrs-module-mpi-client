/**
 * Portions Copyright 2015-2018 Mohawk College of Applied Arts and Technology
 * Portions Copyright (c) 2014-2020 Fyfe Software Inc.
 *  
 * Licensed under the Apache License, Version 2.0 (the "License"); you 
 * may not use this file except in compliance with the License. You may 
 * obtain a copy of the License at 
 * 
 * http://www.apache.org/licenses/LICENSE-2.0 
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the 
 * License for the specific language governing permissions and limitations under 
 * the License.
 */
package org.openmrs.module.santedb.mpiclient.model;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.openmrs.*;

/**
 * Represents temporary structure in memory to use for storing patient data
 * @author justin
 *
 */
public class MpiPatient extends Patient {

	
	// Backing field for relationships
	private List<Relationship> relationships = new ArrayList<>();
	private Set<Obs> 	patientObservations = new HashSet<>();

	private String sourceLocation;

	public MpiPatient() {
		
	}
	public MpiPatient(Patient patient) {

		for(PersonAddress pa : patient.getAddresses()){
			pa.setPerson(this);
			pa.setUuid(null);
			pa.setId(null);
		}
		this.setAddresses(patient.getAddresses());
		for(PersonName pn : patient.getNames()){
			pn.setPerson(this);
			pn.setUuid(null);
			pn.setId(null);
		}
		this.setNames(patient.getNames());
		for(PatientIdentifier pi : patient.getIdentifiers()){
			pi.setPatient(this);
			pi.setUuid(null);
			pi.setId(null);
		}
		this.setIdentifiers(patient.getIdentifiers());
		for(PersonAttribute pa : patient.getAttributes()){
			pa.setPerson(this);
			pa.setUuid(null);
			pa.setId(null);
		}
		this.setAttributes(patient.getAttributes());

		this.setDeathDate(patient.getDeathDate());
		this.setGender(patient.getGender());
		this.setBirthdateEstimated(patient.getBirthdateEstimated());
		this.setBirthdate(patient.getBirthdate());
		//		retVal.setAttributes(this.getAttributes());
		this.setDead(patient.getDead());
	}

	/**
	 * Get relationships of the patient
	 * @return
	 */
	public List<Relationship> getRelationships() { return this.relationships; }
	
	/**
	 * Add a relationship to the patient
	 * @param relationship
	 */
	public void addRelationship(Relationship relationship) {
		this.relationships.add(relationship);
	}

	public String getSourceLocation() {
		return sourceLocation;
	}

	public void setSourceLocation(String sourceLocation) {
		this.sourceLocation = sourceLocation;
	}

	public Set<Obs> getPatientObservations() {
		return patientObservations;
	}

	public void addPatientObservation(Obs obs) {
		this.patientObservations.add(obs);
	}

	/**
	 * Convert this MpiPatient to an actual patient
	 */
	public Patient toPatient() {

		Patient retVal = new Patient();
		for(PersonAddress pa : this.getAddresses()){
			pa.setPerson(retVal);
			pa.setUuid(null);
			pa.setId(null);
		}
		retVal.setAddresses(this.getAddresses());
		for(PersonName pn : this.getNames()){
			pn.setPerson(retVal);
			pn.setUuid(null);
			pn.setId(null);
		}
		retVal.setNames(this.getNames());
		for(PatientIdentifier pi : this.getIdentifiers()){
			pi.setPatient(retVal);
			pi.setUuid(null);
			pi.setId(null);
		}
		retVal.setIdentifiers(this.getIdentifiers());
		for(PersonAttribute pa : this.getAttributes()){
			pa.setPerson(retVal);
			pa.setUuid(null);
			pa.setId(null);
		}
		retVal.setAttributes(this.getAttributes());
		
		retVal.setDeathDate(this.getDeathDate());
		retVal.setGender(this.getGender());
		retVal.setBirthdateEstimated(this.getBirthdateEstimated());
		retVal.setBirthdate(this.getBirthdate());
//		retVal.setAttributes(this.getAttributes());
		retVal.setDead(this.getDead());
		return retVal;
	}
}
