package org.openmrs.module.santedb.mpiclient.aop;

import java.util.HashMap;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.openmrs.Patient;
import org.openmrs.PatientIdentifier;
import org.openmrs.PatientIdentifierType;
import org.openmrs.api.context.Context;
import org.openmrs.api.context.UserContext;
import org.openmrs.module.santedb.mpiclient.api.MpiClientService;
import org.openmrs.module.santedb.mpiclient.configuration.MpiClientConfiguration;
import org.openmrs.module.santedb.mpiclient.exception.MpiClientException;

/**
 * Patient update worker
 * @author Justin Fyfe
 *
 */
public class PatientUpdateWorker extends Thread {

	private final Log log = LogFactory.getLog(this.getClass());

	private final MpiClientConfiguration m_configuration = MpiClientConfiguration.getInstance();
	private final UserContext m_userContext;
	
	private Patient m_patient;
	
	/**
	 * Create a new patient update worker
	 * @param patient
	 */
	public PatientUpdateWorker(Patient patient, UserContext ctx ) {
		this.m_patient = patient;
		this.m_userContext = ctx;
	}
	
	/**
	 * Run the process for sync the patient
	 */
	@Override
	public void run() {
		log.info("Sending update to the MPI for new patient data...");
		try
		{
			Context.openSession();
			Context.setUserContext(this.m_userContext);
			MpiClientService hieService = Context.getService(MpiClientService.class);

			hieService.exportPatient(this.m_patient);
	
			// Grab the national health ID for the patient
			if(!this.m_configuration.getNationalPatientIdRoot().isEmpty()) {
				
				// Find the value for the NHID
				HashMap<String, String> identifierMaps = this.m_configuration.getLocalPatientIdentifierTypeMap();
				PatientIdentifierType pit = null;
				for(String key : identifierMaps.keySet())
					if(this.m_configuration.getNationalPatientIdRoot().equals(identifierMaps.get(key)))
					{
						pit = Context.getPatientService().getPatientIdentifierTypeByName(key);
						break;
					}
				
				if(pit != null && this.m_patient.getPatientIdentifier(pit) == null &&
						this.m_patient.getPatientIdentifier(pit) == null) {
					PatientIdentifier pid = hieService.resolvePatientIdentifier(this.m_patient, this.m_configuration.getNationalPatientIdRoot());
					if(pid != null) {
						pid.setPatient(this.m_patient);
						Context.getPatientService().savePatientIdentifier(pid);
					}
				}
			}
			
		}
		catch(MpiClientException e)
		{
			log.error(e);
		}		
		finally {
			Context.closeSession();
		}
	}
}
