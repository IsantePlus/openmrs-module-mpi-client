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
import java.util.List;

import org.openmrs.Patient;
import org.openmrs.PatientIdentifier;
import org.openmrs.PersonAddress;
import org.openmrs.PersonAttribute;
import org.openmrs.PersonName;
import org.openmrs.Relationship;

/**
 * Represents temporary structure in memory to use for storing patient data
 * @author justin
 *
 */
public class MpiPatient extends Patient {

	
	// Backing field for relationships
	private List<Relationship> m_relationships = new ArrayList<Relationship>();
	
	/**
	 * Get relationships of the patient
	 * @return
	 */
	public List<Relationship> getRelationships() { return this.m_relationships; }
	
	/**
	 * Add a relationship to the patient
	 * @param relationship
	 */
	public void addRelationship(Relationship relationship) {
		this.m_relationships.add(relationship);
	}
	
	/**
	 * Convert this MpiPatient to an actual patient
	 */
	public Patient toPatient() {

		Patient retVal = new Patient();
		for(PersonAddress pa : this.getAddresses())
			pa.setPerson(retVal);
		retVal.setAddresses(this.getAddresses());
		for(PersonName pn : this.getNames())
			pn.setPerson(retVal);
		retVal.setNames(this.getNames());
		for(PatientIdentifier pi : this.getIdentifiers())
			pi.setPatient(retVal);
		retVal.setIdentifiers(this.getIdentifiers());
		for(PersonAttribute pa : this.getAttributes())
			pa.setPerson(retVal);
		retVal.setAttributes(this.getAttributes());
		
		retVal.setDeathDate(this.getDeathDate());
		retVal.setGender(this.getGender());
		retVal.setBirthdateEstimated(this.getBirthdateEstimated());
		retVal.setBirthdate(this.getBirthdate());
		retVal.setAttributes(this.getAttributes());
		retVal.setDead(this.getDead());
		return retVal;
	}
}
