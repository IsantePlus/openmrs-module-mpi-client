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
package org.openmrs.module.santedb.mpiclient.aop;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

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
	
	private Patient m_patient;
	
	/**
	 * Create a new patient update worker
	 * @param patient
	 */
	public PatientSyncWorker(Patient patient, UserContext ctx) {
		this.m_patient = patient;
		this.m_userContext = ctx;
	}
	
	/**
	 * Run the process for sync the patient
	 */
	@Override
	public void run() {
		log.info("Running the patient sync process...");
		try
		{
			// Make sure we only XREF once
			synchronized (s_xref) {
				if(s_xref.contains(this.m_patient.getUuid()))
				{
					log.warn(String.format("Patient %s is already being cross referenced", this.m_patient.getUuid()));
					return;
				}
				else 
						s_xref.add(this.m_patient.getUuid());
			}
			
			Context.openSession();
			Context.setUserContext(this.m_userContext);
			MpiClientService hieService = Context.getService(MpiClientService.class);
			// Grab the national health ID for the patient
			if(this.m_configuration.getAutomaticCrossReferenceDomains() != null) {
				
				// Find the value for the NHID
				HashMap<String, String> identifierMaps = this.m_configuration.getLocalPatientIdentifierTypeMap();
				
				// Automatically xref patients in the identity domains
				String[] autoXrefDomains = this.m_configuration.getAutomaticCrossReferenceDomains().split(",");
				if(autoXrefDomains.length == 0)
					autoXrefDomains = new String[] { this.m_configuration.getNationalPatientIdRoot() };
				
				for(String xrefDomain : autoXrefDomains) {
					
					log.info(String.format("Will XREF %s with %s", this.m_patient.getUuid(), xrefDomain));
					
					PatientIdentifierType pit = null;
					for(String key : identifierMaps.keySet())
						if(xrefDomain.equals(identifierMaps.get(key)))
						{
							pit = Context.getPatientService().getPatientIdentifierTypeByName(key);
							if(pit == null)
								log.warn(String.format("%s is supposed to map to %s but getIdentifierTypeByName returned null", xrefDomain, key));
							break;
						}
					
					if(pit != null && this.m_patient.getPatientIdentifier(pit) == null) {
						PatientIdentifier pid = hieService.resolvePatientIdentifier(this.m_patient, xrefDomain);
						List<PatientIdentifierType> pitList = new ArrayList<PatientIdentifierType>();
						pitList.add(pit);

						// Already exists 
						if(pid != null && Context.getPatientService().getPatientIdentifiers(pid.getIdentifier(), pitList, null, null, null).size() != 0)
							log.warn(String.format("Identifier %s already exists", pid.getIdentifier()));
						else if(pid != null) {
							pid.setPatient(this.m_patient);
							Context.getPatientService().savePatientIdentifier(pid);
						}
						else 
						{
							log.info(String.format("MPI does not have an ID for patient %s in domain %s", this.m_patient.getId(), xrefDomain));
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
				s_xref.remove(this.m_patient.getUuid());
			}
			
			Context.closeSession();
		}
	}
}
