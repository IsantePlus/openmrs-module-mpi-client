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

import org.openmrs.Patient;
import org.openmrs.api.PatientService;
import org.openmrs.api.context.Context;
import org.springframework.aop.AfterReturningAdvice;

/**
 * After returning from the save method of the Patient service
 */
public class PatientUpdateAdvice implements AfterReturningAdvice {
	
	
	
	/**
	 * Runs everytime a patient a updated
	 * @see org.springframework.aop.AfterReturningAdvice#afterReturning(java.lang.Object, java.lang.reflect.Method, java.lang.Object[], java.lang.Object)
	 */
	public void afterReturning(Object returnValue, Method method, Object[] args, Object target) throws Throwable {
		
		
		if(method.getName().equals("savePatient") && target instanceof PatientService)
		{
			PatientUpdateWorker worker = new PatientUpdateWorker((Patient)returnValue, Context.getUserContext());
			worker.start();
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
