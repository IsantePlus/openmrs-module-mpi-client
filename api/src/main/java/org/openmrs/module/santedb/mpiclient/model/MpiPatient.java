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
package org.openmrs.module.santedb.mpiclient.model;

import java.util.ArrayList;
import java.util.List;

import org.openmrs.Patient;
import org.openmrs.Relationship;

/**
 * Represents temporary structure in memory to use for storing patient data
 * @author justin
 *
 */
public class MpiPatient extends Patient {

	
	// Backing field for relationships
	private List<Relationship> m_relationships = new ArrayList<Relationship>();
	
	/**
	 * Get relationships of the patient
	 * @return
	 */
	public List<Relationship> getRelationships() { return this.m_relationships; }
	
	/**
	 * Add a relationship to the patient
	 * @param relationship
	 */
	public void addRelationship(Relationship relationship) {
		this.m_relationships.add(relationship);
	}
	
}
