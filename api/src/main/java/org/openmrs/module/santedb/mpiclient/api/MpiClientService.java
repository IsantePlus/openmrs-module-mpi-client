package org.openmrs.module.santedb.mpiclient.api;

import java.util.Date;
import java.util.List;

import org.openmrs.Encounter;
import org.openmrs.Patient;
import org.openmrs.PatientIdentifier;
import org.openmrs.api.OpenmrsService;
import org.openmrs.module.santedb.mpiclient.exception.MpiClientException;
import org.openmrs.module.santedb.mpiclient.model.MpiPatient;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implementation of the HealthInformationExchangeService
 * @author Justin
 */
@Transactional(rollbackFor=MpiClientException.class)
public interface MpiClientService extends OpenmrsService {

	/**
	 * Searches the PDQ supplier for patients matching the specified search string and returns
	 * patients matching the supplied string 
	 * @param patientSearchString
	 * @return
	 */
	public List<MpiPatient> searchPatient(String familyName, String givenName, Date dateOfBirth, boolean fuzzyDate, String gender, PatientIdentifier patientIdentifier, PatientIdentifier mothersIdentifier) throws MpiClientException;
	
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
	 * Forces an update of the patient's ECID data
	 * @param patient
	 */
	public void synchronizePatientEnterpriseId(Patient patient) throws MpiClientException;
	
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
	 * @param patient
	 */
	public void exportPatient(Patient patient) throws MpiClientException;

	/**
	 * Export patient demographic record to the CR
	 * @param patient
	 */
	public void updatePatient(Patient patient) throws MpiClientException;

	
	
}
