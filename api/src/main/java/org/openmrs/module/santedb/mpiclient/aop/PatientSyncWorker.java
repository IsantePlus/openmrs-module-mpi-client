package org.openmrs.module.santedb.mpiclient.aop;

import java.util.HashMap;
import java.util.HashSet;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.openmrs.Patient;
import org.openmrs.PatientIdentifier;
import org.openmrs.PatientIdentifierType;
import org.openmrs.api.context.Context;
import org.openmrs.api.context.UserContext;
import org.openmrs.module.santedb.mpiclient.api.MpiClientService;
import org.openmrs.module.santedb.mpiclient.configuration.MpiClientConfiguration;

public class PatientSyncWorker extends Thread {


	private final static HashSet<String> s_xref = new HashSet<String>();
	
	private final Log log = LogFactory.getLog(this.getClass());

	private final MpiClientConfiguration m_configuration = MpiClientConfiguration.getInstance();
	private final UserContext m_userContext;
	
	private String m_patientId;
	
	/**
	 * Create a new patient update worker
	 * @param patient
	 */
	public PatientSyncWorker(String patientId, UserContext ctx ) {
		this.m_patientId = patientId;
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
			// Make sure we only XREF once
			synchronized (s_xref) {
				if(s_xref.contains(this.m_patientId))
				{
					log.warn(String.format("Patient %s is already being cross referenced", this.m_patientId));
					return;
				}
				else 
						s_xref.add(this.m_patientId);
			}
			
			Context.openSession();
			Context.setUserContext(this.m_userContext);
			MpiClientService hieService = Context.getService(MpiClientService.class);
			Patient patient = Context.getPatientService().getPatientByUuid(this.m_patientId);
			// Grab the national health ID for the patient
			if(this.m_configuration.getAutomaticCrossReferenceDomains() != null) {
				
				// Find the value for the NHID
				HashMap<String, String> identifierMaps = this.m_configuration.getLocalPatientIdentifierTypeMap();
				
				// Automatically xref patients in the identity domains
				String[] autoXrefDomains = this.m_configuration.getAutomaticCrossReferenceDomains().split(",");
				if(autoXrefDomains.length == 0)
					autoXrefDomains = new String[] { this.m_configuration.getNationalPatientIdRoot() };
				
				for(String xrefDomain : autoXrefDomains) {
					
					log.info(String.format("Will XREF %s with %s", this.m_patientId, xrefDomain));
					
					PatientIdentifierType pit = null;
					for(String key : identifierMaps.keySet())
						if(xrefDomain.equals(identifierMaps.get(key)))
						{
							pit = Context.getPatientService().getPatientIdentifierTypeByName(key);
							break;
						}
					
					if(pit != null && patient.getPatientIdentifier(pit) == null) {
						PatientIdentifier pid = hieService.resolvePatientIdentifier(patient, xrefDomain);
						if(pid != null) {
							pid.setPatient(patient);
							Context.getPatientService().savePatientIdentifier(pid);
						}
						else 
						{
							log.info(String.format("MPI does not have an ID for patient %s in domain %s", patient.getId(), xrefDomain));
						}
					}
					else if(pit == null)
						log.warn(String.format("Identity domain %s has no local equivalent", xrefDomain));
					else
						log.warn(String.format("Patient already has local identifier in domain %s", xrefDomain));
				}
			}
		}
		catch(Exception e)
		{
			log.error(e);
		}		
		finally {
			synchronized (s_xref) {
				s_xref.remove(this.m_patientId);
			}
			
			Context.closeSession();
		}
	}
}
