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
 */package org.openmrs.module.santedb.mpiclient.api;

import java.util.Date;
import java.util.List;

import org.dcm4che3.net.audit.AuditLogger;
import org.openmrs.Patient;
import org.openmrs.PatientIdentifier;
import org.openmrs.module.santedb.mpiclient.exception.MpiClientException;
import org.openmrs.module.santedb.mpiclient.model.MpiPatient;

/**
 * Implementation of the HealthInformationExchangeService
 * @author Justin
 */
public interface MpiClientWorker  {

	/**
	 * Searches the PDQ supplier for patients matching the specified search string and returns
	 * patients matching the supplied string 
	 * @param patientSearchString
	 * @return
	 */
	public List<MpiPatient> searchPatient(String familyName, String givenName, Date dateOfBirth, boolean fuzzyDate, String gender, String stateOrRegion, String cityOrTownship, PatientIdentifier patientIdentifier,  PatientIdentifier mothersIdentifier, String nextOfKinName, String birthPlace) throws MpiClientException;
	
	/**
	 * Searches for patients with the specified patient identity string 
	 */
	public MpiPatient getPatient(String identifier, String assigningAuthority) throws MpiClientException;
	
	/**
	 * Resolve an HIE patient identifier 
	 * @throws MpiClientException 
	 */
	public PatientIdentifier resolvePatientIdentifier(Patient patient, String toAssigningAuthority) throws MpiClientException;
	
	/**
	 * Import the specified patient data from the PDQ supplier
	 * @param identifier
	 * @param asigningAuthority
	 * @return
	 * @throws MpiClientException 
	 */
	public Patient importPatient(MpiPatient patient) throws MpiClientException;
	
	/**
	 * Export patient demographic record to the CR
	 * @param patient
	 */
	public void exportPatient(Patient patient) throws MpiClientException;

	/**
	 * Export patient demographic record to the CR
	 * @param patient
	 */
	public void updatePatient(Patient patient) throws MpiClientException;

	/**
	 * Get the audit logger
	 */
	public AuditLogger getAuditLogger();
	
	
}
