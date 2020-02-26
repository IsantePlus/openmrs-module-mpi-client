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
package org.openmrs.module.santedb.mpiclient.dao;

import org.openmrs.Patient;
import org.openmrs.PatientIdentifierType;

/**
 * A DAO that is used by the HIE to assist in the maintenance of OpenMRS
 * data store with an HIE
 */
public interface MpiClientDao {

	/**
	 * Get a patient by identifier, throws a multiple exception when there are multiple / conflicting patients with the specified identifier
	 */
	public Patient getPatientByIdentifier(String idNumber, PatientIdentifierType idType);
	
}
