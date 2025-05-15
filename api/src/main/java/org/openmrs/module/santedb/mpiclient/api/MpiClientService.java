/**
 * Portions Copyright 2015-2018 Mohawk College of Applied Arts and Technology
 * Portions Copyright (c) 2014-2020 Fyfe Software Inc.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License"); you
 * may not use this file except in compliance with the License. You may
 * obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package org.openmrs.module.santedb.mpiclient.api;

import java.util.Date;
import java.util.List;
import java.util.Map;

import org.dcm4che3.net.audit.AuditLogger;
import org.openmrs.Patient;
import org.openmrs.PatientIdentifier;
import org.openmrs.api.OpenmrsService;
import org.openmrs.module.santedb.mpiclient.exception.MpiClientException;
import org.openmrs.module.santedb.mpiclient.model.MpiPatient;
import org.openmrs.module.santedb.mpiclient.model.MpiPatientExport;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implementation of the HealthInformationExchangeService
 * @author Justin
 */
@Transactional(rollbackFor = MpiClientException.class)
public interface MpiClientService extends OpenmrsService {

    /**
     * Searches the PDQ supplier for patients matching the specified search string and returns
     * patients matching the supplied string
     * @param patientSearchString
     * @return
     */
    List<MpiPatient> searchPatient(String familyName, String givenName, Date dateOfBirth, boolean fuzzyDate,
                                   String gender, String stateOrRegion, String cityOrTownship, PatientIdentifier patientIdentifier,
                                   PatientIdentifier mothersIdentifier, String nextOfKinName, String birthPlace,
                                   Map<String, Object> otherDataPoints) throws MpiClientException;

    /**
     * Searches the PDQ supplier for patients matching the specified search patient and returns patients matching the supplied object
     * @param patient - OpenMRS patient to find matches for
     * @param  otherDataPoints - additional data points for patient search
     * @return Wrapper list containing OpenMRS patients
     */
    List<MpiPatient> searchPatient(Patient patient, Map<String, Object> otherDataPoints) throws MpiClientException;

    /**
     * Searches for patients with the specified patient identity string
     */
    MpiPatient getPatient(String identifier, String assigningAuthority) throws MpiClientException;
	
	
		/**
	 * Get List of patient using specified identifier and AA
	 */
	List<MpiPatient> getPatientList(String identifier, String assigningAuthority) throws MpiClientException;



    /**
     * Resolve an HIE patient identifier
     * @throws MpiClientException
     */
    PatientIdentifier resolvePatientIdentifier(Patient patient, String toAssigningAuthority) throws MpiClientException;

    /**
     * Forces an update of the patient's ECID data
     * @param patient
     */
    void synchronizePatientEnterpriseId(Patient patient) throws MpiClientException;

    /**
     * Import the specified patient data from the PDQ supplier
     * @param identifier
     * @param asigningAuthority
     * @return
     * @throws MpiClientException
     */
    public Patient importPatient(MpiPatient patient) throws MpiClientException;

    /**
     * Matches an external patient with an internal
     */
    public Patient matchWithExistingPatient(Patient remotePatient);

    /**
     * Export patient demographic record to the CR
     * @param patientExport
     */
    public void exportPatient(MpiPatientExport patientExport) throws MpiClientException;

    /**
     * Export patient demographic record to the CR
     * @param patient
     */
    public void updatePatient(MpiPatientExport patientExport) throws MpiClientException;

    /**
     * Get the audit logger
     */
    public AuditLogger getAuditLogger();

	/**
	 * Get patient using specified identifier and AA
	 */
	List<MpiPatient> getPatientList(String identifier, String assigningAuthority) throws MpiClientException;


}
