package org.openmrs.module.santedb.client.api;

import java.util.Date;
import java.util.List;

import org.openmrs.Encounter;
import org.openmrs.Patient;
import org.openmrs.PatientIdentifier;
import org.openmrs.api.OpenmrsService;
import org.openmrs.module.santedb.client.exception.SanteDbClientException;
import org.openmrs.module.santedb.client.hie.model.DocumentInfo;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implementation of the HealthInformationExchangeService
 * @author Justin
 */
@Transactional(rollbackFor=SanteDbClientException.class)
public interface SanteDbClientService extends OpenmrsService {

	/**
	 * Searches the PDQ supplier for patients matching the specified search string and returns
	 * patients matching the supplied string 
	 * @param patientSearchString
	 * @return
	 */
	public List<Patient> searchPatient(String familyName, String givenName, Date dateOfBirth, boolean fuzzyDate, String gender, PatientIdentifier patientIdentifier, PatientIdentifier mothersIdentifier) throws SanteDbClientException;
	
	/**
	 * Searches for patients with the specified patient identity string 
	 */
	public Patient getPatient(String identifier, String assigningAuthority) throws SanteDbClientException;
	
	/**
	 * Resolve an HIE patient identifier 
	 * @throws SanteDbClientException 
	 */
	public PatientIdentifier resolvePatientIdentifier(Patient patient, String toAssigningAuthority) throws SanteDbClientException;
	
	/**
	 * Forces an update of the patient's ECID data
	 * @param patient
	 */
	public void updatePatientEcid(Patient patient) throws SanteDbClientException;
	
	/**
	 * Import the specified patient data from the PDQ supplier
	 * @param identifier
	 * @param asigningAuthority
	 * @return
	 * @throws SanteDbClientException 
	 */
	public Patient importPatient(Patient patient) throws SanteDbClientException;
	
	/**
	 * Matches an external patient with an internal 
	 */
	public Patient matchWithExistingPatient(Patient remotePatient);
	
	/**
	 * Export patient demographic record to the CR
	 * @param patient
	 */
	public void exportPatient(Patient patient) throws SanteDbClientException;

	/**
	 * Export patient demographic record to the CR
	 * @param patient
	 */
	public void updatePatient(Patient patient) throws SanteDbClientException;

	
	/**
	 * Get all HIE documents for the specified patient
	 */
	public List<DocumentInfo> getDocuments(Patient patient) throws SanteDbClientException;
	
	/**
	 * Get the document contents from the HIE
	 */
	public byte[] fetchDocument(DocumentInfo document) throws SanteDbClientException;
	
	/**
	 * Perform a document import of the specified document information object
	 */
	public Encounter importDocument(DocumentInfo document) throws SanteDbClientException;
	
	/**
	 * Export the specified encounters as a document to the HIE
	 * @param encounters
	 * @return
	 */
	public DocumentInfo exportDocument(byte[] documentContent, DocumentInfo info) throws SanteDbClientException;

	/**
	 * Query for documents with the matching criteria
	 * @throws SanteDbClientException 
	 */
	public List<DocumentInfo> queryDocuments(Patient patientInfo, boolean oddOnly, Date sinceDate,
			String formatCode, String formatCodingScheme) throws SanteDbClientException;

	
}
