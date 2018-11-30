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
package org.openmrs.module.santedb.mpiclient.dao.impl;

import org.hibernate.Criteria;
import org.hibernate.SessionFactory;
import org.hibernate.criterion.Restrictions;
import org.openmrs.Patient;
import org.openmrs.PatientIdentifier;
import org.openmrs.PatientIdentifierType;
import org.openmrs.module.santedb.mpiclient.dao.MpiClientDao;

/**
 * Implementation of the HIE client DAO for hibernate
 * @author Justin
 *
 */
public class HibernateMpiClientDao implements
		MpiClientDao {

	// Session factory
	private SessionFactory sessionFactory;
	
	/**
	 * @return the sessionFactory
	 */
	public SessionFactory getSessionFactory() {
		return sessionFactory;
	}

	/**
	 * @param sessionFactory the sessionFactory to set
	 */
	public void setSessionFactory(SessionFactory sessionFactory) {
		this.sessionFactory = sessionFactory;
	}

	/**
	 * Get patients by identifier
	 */
	public Patient getPatientByIdentifier(String idNumber,
			PatientIdentifierType idType) {
		Criteria idCriteria = this.sessionFactory.getCurrentSession().createCriteria(PatientIdentifier.class);
		idCriteria.add(Restrictions.eq("identifier", idNumber))
			.add(Restrictions.eq("identifierType", idType));
		
		// Get the identifier
		PatientIdentifier pid = (PatientIdentifier)idCriteria.uniqueResult();
		if(pid == null) return null;
		
		// Return patient
		return pid.getPatient();
	}

}
