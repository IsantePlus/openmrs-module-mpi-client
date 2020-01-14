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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.UnknownHostException;
import java.security.GeneralSecurityException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.dcm4che3.audit.AuditMessage;
import org.dcm4che3.audit.AuditMessages;
import org.dcm4che3.net.Connection;
import org.dcm4che3.net.Device;
import org.dcm4che3.net.audit.AuditLogger;
import org.dcm4che3.net.audit.AuditRecordRepository;
import org.marc.everest.datatypes.II;
import org.marc.everest.formatters.interfaces.IXmlStructureFormatter;
import org.openmrs.Encounter;
import org.openmrs.Patient;
import org.openmrs.PatientIdentifier;
import org.openmrs.PersonAddress;
import org.openmrs.PersonName;
import org.openmrs.Relationship;
import org.openmrs.Visit;
import org.openmrs.PatientIdentifierType.LocationBehavior;
import org.openmrs.api.APIException;
import org.openmrs.api.DuplicateIdentifierException;
import org.openmrs.api.context.Context;
import org.openmrs.api.impl.BaseOpenmrsService;
import org.openmrs.module.santedb.mpiclient.api.MpiClientService;
import org.openmrs.module.santedb.mpiclient.configuration.MpiClientConfiguration;
import org.openmrs.module.santedb.mpiclient.dao.MpiClientDao;
import org.openmrs.module.santedb.mpiclient.exception.MpiClientException;
import org.openmrs.module.santedb.mpiclient.model.MpiPatient;
import org.openmrs.module.santedb.mpiclient.util.AuditUtil;
import org.openmrs.module.santedb.mpiclient.util.MessageDispatchWorker;
import org.openmrs.module.santedb.mpiclient.util.MessageUtil;

import ca.uhn.hl7v2.HL7Exception;
import ca.uhn.hl7v2.model.Message;
import ca.uhn.hl7v2.model.v231.datatype.ED;
import ca.uhn.hl7v2.model.v231.datatype.ELD;
import ca.uhn.hl7v2.model.v231.message.ACK;
import ca.uhn.hl7v2.model.v25.message.QBP_Q21;
import ca.uhn.hl7v2.parser.PipeParser;
import ca.uhn.hl7v2.util.Terser;

/**
 * Implementation of the health information exchange service
 * @author Justin
 *
 */
public class MpiClientServiceImpl extends BaseOpenmrsService
		implements MpiClientService {


	// The wrapped service
	private MpiClientService m_wrappedService;
	
	/**
	 * @summary Creates a new instance of the MPI Client Service Implementation
	 */
	public MpiClientServiceImpl() {
		if(MpiClientConfiguration.getInstance().getMessageFormat().equals("fhir"))
			this.m_wrappedService = new FhirMpiClientServiceImpl(); // TODO: FHIR implementation
		else 
			this.m_wrappedService = new HL7MpiClientServiceImpl();
	}

	/**
	 * Search patient from wrapped
	 */
	@Override
	public List<MpiPatient> searchPatient(String familyName, String givenName, Date dateOfBirth, boolean fuzzyDate,
			String gender, String stateOrRegion, String cityOrTownship, PatientIdentifier patientIdentifier,
			PatientIdentifier mothersIdentifier, String nextOfKinName, String birthPlace) throws MpiClientException {
		// TODO Auto-generated method stub
		return this.m_wrappedService.searchPatient(familyName, givenName, dateOfBirth, fuzzyDate, gender, stateOrRegion, cityOrTownship, patientIdentifier, mothersIdentifier, nextOfKinName, birthPlace);
	}

	/**
	 * Get patient using specified identifier and AA
	 */
	@Override
	public MpiPatient getPatient(String identifier, String assigningAuthority) throws MpiClientException {
		// TODO Auto-generated method stub
		return this.m_wrappedService.getPatient(identifier, assigningAuthority);
	}

	/**
	 * Resolve patient identifier 
	 */
	@Override
	public PatientIdentifier resolvePatientIdentifier(Patient patient, String toAssigningAuthority)
			throws MpiClientException {
		// TODO Auto-generated method stub
		return this.m_wrappedService.resolvePatientIdentifier(patient, toAssigningAuthority);
	}

	/**
	 * Synchronize patient with enterprise identifier
	 */
	@Override
	public void synchronizePatientEnterpriseId(Patient patient) throws MpiClientException {
		// TODO Auto-generated method stub
		this.m_wrappedService.synchronizePatientEnterpriseId(patient);
		
	}

	/**
	 * Import patient with specified patient data
	 */
	@Override
	public Patient importPatient(MpiPatient patient) throws MpiClientException {
		// TODO Auto-generated method stub
		return this.importPatient(patient);
	}

	/**
	 * Match with an existing patient in OpenMRS
	 */
	@Override
	public Patient matchWithExistingPatient(Patient remotePatient) {
		// TODO Auto-generated method stub
		return this.matchWithExistingPatient(remotePatient);
	}

	/**
	 * Export patient using preferred messaging format
	 */
	@Override
	public void exportPatient(Patient patient) throws MpiClientException {
		// TODO Auto-generated method stub
		this.m_wrappedService.exportPatient(patient);
	}

	/**
	 * Update patient in MPI
	 */
	@Override
	public void updatePatient(Patient patient) throws MpiClientException {
		// TODO Auto-generated method stub
		this.m_wrappedService.updatePatient(patient);
	}

	/**
	 * GEt audit logger
	 */
	@Override
	public AuditLogger getAuditLogger() {
		// TODO Auto-generated method stub
		return this.m_wrappedService.getAuditLogger();
	}
	
}
