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
