package org.openmrs.module.santedb.mpiclient.aop;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.marc.everest.datatypes.generic.LIST;
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
 * 
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
	 * 
	 * @param patient
	 */
	public PatientUpdateWorker(Patient patient, UserContext ctx) {
		this.m_patient = patient;
		this.m_userContext = ctx;
	}

	/**
	 * Run the process for sync the patient
	 */
	@Override
	public void run() {
		log.info("Sending update to the MPI for new patient data...");
		try {
			Context.openSession();
			Context.setUserContext(this.m_userContext);
			MpiClientService hieService = Context.getService(MpiClientService.class);

			hieService.exportPatient(this.m_patient);

			// Grab the national health ID for the patient
			if (this.m_configuration.getAutomaticCrossReferenceDomains() != null) {

				// Find the value for the NHID
				HashMap<String, String> identifierMaps = this.m_configuration.getLocalPatientIdentifierTypeMap();

				// Automatically xref patients in the identity domains
				String[] autoXrefDomains = this.m_configuration.getAutomaticCrossReferenceDomains().split(",");
				if (autoXrefDomains.length == 0)
					autoXrefDomains = new String[] { this.m_configuration.getNationalPatientIdRoot() };

				for (String xrefDomain : autoXrefDomains) {

					log.info(String.format("Will XREF %s with %s", this.m_patient.getId(), xrefDomain));

					PatientIdentifierType pit = null;
					for (String key : identifierMaps.keySet())
						if (xrefDomain.equals(identifierMaps.get(key))) {
							pit = Context.getPatientService().getPatientIdentifierTypeByName(key);
							break;
						}

					List<PatientIdentifierType> pitList = new ArrayList<PatientIdentifierType>();
					pitList.add(pit);
					
					if (pit != null && this.m_patient.getPatientIdentifier(pit) == null) {
						PatientIdentifier pid = hieService.resolvePatientIdentifier(this.m_patient, xrefDomain);
						// Already exists 
						if(pid != null && Context.getPatientService().getPatientIdentifiers(pid.getIdentifier(), pitList, null, null, null).size() != 0)
							log.warn(String.format("Identifier %s already exists", pid.getIdentifier()));
						else if (pid != null) {
							pid.setPatient(this.m_patient);
							Context.getPatientService().savePatientIdentifier(pid);
						} else {
							log.info(String.format("MPI does not have an ID for patient %s in domain %s",
									this.m_patient.getId(), xrefDomain));
						}
					} else if (pit == null)
						log.warn(String.format("Identity domain %s has no local equivalent", xrefDomain));
					else
						log.warn(String.format("Patient already has local identifier in domain %s", xrefDomain));
				}
			}
		} catch (MpiClientException e) {
			log.error(e);
		} finally {
			Context.closeSession();
		}
	}
}
