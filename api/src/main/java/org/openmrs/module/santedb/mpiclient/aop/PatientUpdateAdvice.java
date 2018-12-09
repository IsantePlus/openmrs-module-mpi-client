/**
 * Original Copyright (c) 2014-2018 Justin Fyfe (Fyfe Software Inc.) 
 * Copyright (c) 2018 SanteDB Community
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

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.marc.everest.datatypes.TS;
import org.openmrs.Patient;
import org.openmrs.PatientIdentifier;
import org.openmrs.PatientIdentifierType;
import org.openmrs.PersonAttribute;
import org.openmrs.api.PatientService;
import org.openmrs.api.context.Context;
import org.openmrs.module.santedb.mpiclient.MpiClientActivator;
import org.openmrs.module.santedb.mpiclient.api.MpiClientService;
import org.openmrs.module.santedb.mpiclient.configuration.MpiClientConfiguration;
import org.openmrs.module.santedb.mpiclient.exception.MpiClientException;
import org.springframework.aop.AfterReturningAdvice;

/**
 * After returning from the save method of the Patient service
 */
public class PatientUpdateAdvice implements AfterReturningAdvice {
	
	private final Log log = LogFactory.getLog(this.getClass());

	private final MpiClientConfiguration m_configuration = MpiClientConfiguration.getInstance();
	
	/**
	 * Runs everytime a patient a updated
	 * @see org.springframework.aop.AfterReturningAdvice#afterReturning(java.lang.Object, java.lang.reflect.Method, java.lang.Object[], java.lang.Object)
	 */
	public void afterReturning(Object returnValue, Method method, Object[] args, Object target) throws Throwable {
		if(method.getName().equals("savePatient") && target instanceof PatientService)
		{
			log.info("Sending update to the MPI for new patient data...");
			try
			{
				Patient patient = (Patient)returnValue;
				MpiClientService hieService = Context.getService(MpiClientService.class);

				hieService.exportPatient(patient);
		
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
					
					if(pit != null && patient.getPatientIdentifier(pit) == null &&
							patient.getPatientIdentifier(pit) == null) {
						PatientIdentifier pid = hieService.resolvePatientIdentifier(patient, this.m_configuration.getNationalPatientIdRoot());
						if(pid != null) {
							pid.setPatient(patient);
							Context.getPatientService().savePatientIdentifier(pid);
						}
					}
				}
				
			}
			catch(MpiClientException e)
			{
				log.error(e);
				throw e;
			}
		}
		else if(method.getName().equals("mergePatients") && target instanceof PatientService) {
			// TODO:
//			log.info("Sending patient merge to the MPI ...");
//			try
//			{
//				
//				Patient survivor = (Patient)returnValue;
//				List<Patient> nonSurvivors = null;
//				if(args[1] instanceof List)
//					nonSurvivors = (List<Patient>)args[1];
//				else if(args[1] instanceof Patient) {
//					nonSurvivors = new ArrayList<Patient>();
//					nonSurvivors.add((Patient)args[1]);
//				}
//				
//				MpiClientService hieService = Context.getService(MpiClientService.class);
//
//				//hieService.mergePatient(survivor, nonSurvivors);
//				
//			}
//			catch(MpiClientException e)
//			{
//				log.error(e);
//				throw e;
//			}
		}
	}
	
}
