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
package org.openmrs.module.santedb.mpiclient.api.impl;

import java.util.Date;
import java.util.List;

import org.dcm4che3.net.audit.AuditLogger;
import org.openmrs.Patient;
import org.openmrs.PatientIdentifier;
import org.openmrs.api.impl.BaseOpenmrsService;
import org.openmrs.module.santedb.mpiclient.api.MpiClientService;
import org.openmrs.module.santedb.mpiclient.configuration.MpiClientConfiguration;
import org.openmrs.module.santedb.mpiclient.dao.MpiClientDao;
import org.openmrs.module.santedb.mpiclient.exception.MpiClientException;
import org.openmrs.module.santedb.mpiclient.model.MpiPatient;

/**
 * Implementation of the health information exchange service
 * @author Justin
 *
 */
public class MpiClientServiceImpl extends BaseOpenmrsService
		implements MpiClientService {

	private FhirMpiClientServiceImpl m_fhirService;
	private HL7MpiClientServiceImpl m_hl7Service;

	// DAO
		private MpiClientDao dao;
		
	/**
	 * @param dao the dao to set
	 */
	public void setDao(MpiClientDao dao) {
		this.dao = dao;
		this.m_hl7Service.setDao(dao);
	}

	/**
	 * @summary Creates a new instance of the MPI Client Service Implementation
	 */
	public MpiClientServiceImpl() {
		this.m_fhirService = new FhirMpiClientServiceImpl(); // TODO: FHIR implementation
		this.m_hl7Service = new HL7MpiClientServiceImpl();
	}

	/**
	 * Search patient from wrapped
	 */
	@Override
	public List<MpiPatient> searchPatient(String familyName, String givenName, Date dateOfBirth, boolean fuzzyDate,
			String gender, String stateOrRegion, String cityOrTownship, PatientIdentifier patientIdentifier,
			PatientIdentifier mothersIdentifier, String nextOfKinName, String birthPlace) throws MpiClientException {
		if(MpiClientConfiguration.getInstance().getMessageFormat().equals("fhir"))
			return this.m_fhirService.searchPatient(familyName, givenName, dateOfBirth, fuzzyDate, gender, stateOrRegion, cityOrTownship, patientIdentifier, mothersIdentifier, nextOfKinName, birthPlace);
		else 
			return this.m_hl7Service.searchPatient(familyName, givenName, dateOfBirth, fuzzyDate, gender, stateOrRegion, cityOrTownship, patientIdentifier, mothersIdentifier, nextOfKinName, birthPlace);
			
	}

	/**
	 * Get patient using specified identifier and AA
	 */
	@Override
	public MpiPatient getPatient(String identifier, String assigningAuthority) throws MpiClientException {
		// TODO Auto-generated method stub
		if(MpiClientConfiguration.getInstance().getMessageFormat().equals("fhir"))
			return this.m_fhirService.getPatient(identifier, assigningAuthority);
		else 
			return this.m_hl7Service.getPatient(identifier, assigningAuthority);
	}

	/**
	 * Resolve patient identifier 
	 */
	@Override
	public PatientIdentifier resolvePatientIdentifier(Patient patient, String toAssigningAuthority)
			throws MpiClientException {
		// TODO Auto-generated method stub
		if(MpiClientConfiguration.getInstance().getMessageFormat().equals("fhir"))
			return this.m_fhirService.resolvePatientIdentifier(patient, toAssigningAuthority);
		else 
			return this.m_hl7Service.resolvePatientIdentifier(patient, toAssigningAuthority);
	}

	/**
	 * Synchronize patient with enterprise identifier
	 */
	@Override
	public void synchronizePatientEnterpriseId(Patient patient) throws MpiClientException {
		// TODO Auto-generated method stub
		if(MpiClientConfiguration.getInstance().getMessageFormat().equals("fhir"))
			this.m_fhirService.synchronizePatientEnterpriseId(patient);
		else 
			this.m_hl7Service.synchronizePatientEnterpriseId(patient);
	}

	/**
	 * Import patient with specified patient data
	 */
	@Override
	public Patient importPatient(MpiPatient patient) throws MpiClientException {
		// TODO Auto-generated method stub
		if(MpiClientConfiguration.getInstance().getMessageFormat().equals("fhir"))
			return this.m_fhirService.importPatient(patient);
		else 
			return this.m_hl7Service.importPatient(patient);
	}

	/**
	 * Match with an existing patient in OpenMRS
	 */
	@Override
	public Patient matchWithExistingPatient(Patient remotePatient) {
		// TODO Auto-generated method stub
		if(MpiClientConfiguration.getInstance().getMessageFormat().equals("fhir"))
			return this.m_fhirService.matchWithExistingPatient(remotePatient);
		else 
			return this.m_hl7Service.matchWithExistingPatient(remotePatient);
	}

	/**
	 * Export patient using preferred messaging format
	 */
	@Override
	public void exportPatient(Patient patient) throws MpiClientException {
		// TODO Auto-generated method stub
		if(MpiClientConfiguration.getInstance().getMessageFormat().equals("fhir"))
			this.m_fhirService.exportPatient(patient);
		else 
			this.m_hl7Service.exportPatient(patient);
	}

	/**
	 * Update patient in MPI
	 */
	@Override
	public void updatePatient(Patient patient) throws MpiClientException {
		// TODO Auto-generated method stub
		if(MpiClientConfiguration.getInstance().getMessageFormat().equals("fhir"))
			this.m_fhirService.updatePatient(patient);
		else 
			this.m_hl7Service.updatePatient(patient);
	}

	/**
	 * GEt audit logger
	 */
	@Override
	public AuditLogger getAuditLogger() {
		// TODO Auto-generated method stub
		if(MpiClientConfiguration.getInstance().getMessageFormat().equals("fhir"))
			return this.m_fhirService.getAuditLogger();
		else 
			return this.m_hl7Service.getAuditLogger();
	}
	
}
