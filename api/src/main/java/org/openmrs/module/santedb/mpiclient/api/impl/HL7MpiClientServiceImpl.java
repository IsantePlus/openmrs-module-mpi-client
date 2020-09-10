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
package org.openmrs.module.santedb.mpiclient.api.impl;

import java.net.UnknownHostException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.dcm4che3.audit.AuditMessage;
import org.dcm4che3.net.audit.AuditLogger;
import org.marc.everest.datatypes.II;
import org.openmrs.Patient;
import org.openmrs.PatientIdentifier;
import org.openmrs.PatientIdentifierType.LocationBehavior;
import org.openmrs.PersonAddress;
import org.openmrs.PersonName;
import org.openmrs.Relationship;
import org.openmrs.api.APIException;
import org.openmrs.api.DuplicateIdentifierException;
import org.openmrs.api.context.Context;
import org.openmrs.module.santedb.mpiclient.api.MpiClientService;
import org.openmrs.module.santedb.mpiclient.api.MpiClientWorker;
import org.openmrs.module.santedb.mpiclient.configuration.MpiClientConfiguration;
import org.openmrs.module.santedb.mpiclient.dao.MpiClientDao;
import org.openmrs.module.santedb.mpiclient.exception.MpiClientException;
import org.openmrs.module.santedb.mpiclient.model.MpiPatient;
import org.openmrs.module.santedb.mpiclient.model.MpiPatientExport;
import org.openmrs.module.santedb.mpiclient.util.AuditUtil;
import org.openmrs.module.santedb.mpiclient.util.MessageDispatchWorker;
import org.openmrs.module.santedb.mpiclient.util.MessageUtil;

import ca.uhn.hl7v2.HL7Exception;
import ca.uhn.hl7v2.model.Message;
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
public class HL7MpiClientServiceImpl 
		implements MpiClientWorker {

	// Lock object
	
	private Object m_lockObject = new Object();
	// Audit logger
	protected AuditLogger m_logger = null;
	// Log
	private static Log log = LogFactory.getLog(HL7MpiClientServiceImpl.class);
	// Message utility
	private MessageUtil m_messageUtil = MessageUtil.getInstance();
	// Get health information exchange information
	private MpiClientConfiguration m_configuration = MpiClientConfiguration.getInstance();

	// DAO
	private MpiClientDao dao;
	
	/**
	 * @param dao the dao to set
	 */
	public void setDao(MpiClientDao dao) {
		this.dao = dao;
	}

	
	/**
	 * Search the PDQ supplier for the specified patient data
	 * @throws MpiClientException 
	 */
	public List<MpiPatient> searchPatient(String familyName, String givenName,
			Date dateOfBirth, boolean fuzzyDate, String gender,
			String stateOrRegion,
			String cityOrTownship,
			PatientIdentifier identifier,
			PatientIdentifier mothersIdentifier,
			String nextOfKinName,
			String birthplace) throws MpiClientException {

		Map<String, String> queryParams = new HashMap<String, String>();
		if(familyName != null && !familyName.isEmpty())
			queryParams.put("@PID.5.1", familyName);
		if(givenName != null && !givenName.isEmpty())
			queryParams.put("@PID.5.2", givenName);
		if(dateOfBirth != null)
		{
			if(fuzzyDate) {
				if(this.m_configuration.getPdqDateFuzz() == 0)
					queryParams.put("@PID.7", new Integer(dateOfBirth.getYear() + 1900).toString());
				else {
					String queryParm = "";
					for(Integer i = dateOfBirth.getYear() - this.m_configuration.getPdqDateFuzz(); i < dateOfBirth.getYear() + this.m_configuration.getPdqDateFuzz(); i++)
					{
						log.warn(String.format("Including results with DOB - %s", i + 1900));
						queryParm += new Integer(i + 1900).toString() + "~";
					}
					queryParams.put("@PID.7", queryParm.substring(0, queryParm.length() - 1));
				}
			}
			else
				queryParams.put("@PID.7", new SimpleDateFormat("yyyyMMdd").format(dateOfBirth));
		}
		if(birthplace != null && !birthplace.isEmpty())
			queryParams.put("@PID.23", birthplace);
		if(nextOfKinName != null && !nextOfKinName.isEmpty())
			queryParams.put("@NK1.2.2.1", nextOfKinName);

		if(gender != null && !gender.isEmpty())
			queryParams.put("@PID.8", gender);
		if(identifier != null)
		{
			queryParams.put("@PID.3.1", identifier.getIdentifier());

			if(identifier.getIdentifierType() != null)
			{
				if(II.isRootOid(new II(identifier.getIdentifierType().getName())))
				{
					queryParams.put("@PID.3.4.2", identifier.getIdentifierType().getName());
					queryParams.put("@PID.3.4.3", "ISO");
				}
				else
					queryParams.put("@PID.3.4", identifier.getIdentifierType().getName());
			}
		}
		if(mothersIdentifier != null)
		{

			queryParams.put("@PID.21.1", mothersIdentifier.getIdentifier());

			if(mothersIdentifier.getIdentifierType() != null)
			{
				if(II.isRootOid(new II(mothersIdentifier.getIdentifierType().getName())))
				{
					queryParams.put("@PID.21.4.2", mothersIdentifier.getIdentifierType().getName());
					queryParams.put("@PID.21.4.3", "ISO");
				}
				else
					queryParams.put("@PID.21.4", mothersIdentifier.getIdentifierType().getName());
			}
		}
		if(stateOrRegion != null && !stateOrRegion.isEmpty())
		{
			queryParams.put("@PID.11.4", stateOrRegion);
		}
		if(cityOrTownship != null && !cityOrTownship.isEmpty())
		{
			queryParams.put("@PID.11.3", cityOrTownship);
		}

		AuditMessage auditMessage = null;
		Message pdqRequest = null;

		// Send the message and construct the result set
		try
		{
			pdqRequest = this.m_messageUtil.createPdqMessage(queryParams);
			Message	response = this.m_messageUtil.sendMessage(pdqRequest, this.m_configuration.getPdqEndpoint(), this.m_configuration.getPdqPort());

			Terser terser = new Terser(response);
			if(!terser.get("/MSA-1").endsWith("A"))
				throw new MpiClientException(String.format("Error querying data :> %s", terser.get("/MSA-1")), response);


			List<MpiPatient> retVal = this.m_messageUtil.interpretPIDSegments(response);
			auditMessage = AuditUtil.getInstance().createPatientSearch(retVal, this.m_configuration.getPdqEndpoint(), (QBP_Q21)pdqRequest);
			return retVal;
		}
		catch(MpiClientException e)
		{
			log.error("Error in PDQ Search", e);
			if(e.getResponseMessage() != null)
				try {
					log.error(new PipeParser().encode(e.getResponseMessage()));
				} catch (HL7Exception e1) {
					// TODO Auto-generated catch block
					e1.printStackTrace();
				}
			throw e;
		}
		catch(Exception e)
		{
			log.error("Error in PDQ Search", e);
			if(pdqRequest != null)
				try {
					auditMessage = AuditUtil.getInstance().createPatientSearch(null, this.m_configuration.getPdqEndpoint(), (QBP_Q21)pdqRequest);
				} catch (UnknownHostException e1) {
					this.log.error("Error creating error audit:", e1);
				}

			throw new MpiClientException(e);
		}
		finally
		{
			if(auditMessage != null)
				try {
					this.getAuditLogger().write(Calendar.getInstance(), auditMessage);
				} catch (Exception e) {
					log.error(e);
				}
		}
	}

	/**
	 * Search the PDQ supplier for the specified patient data with identifier
	 * @throws MpiClientException 
	 */
	public MpiPatient getPatient(String identifier,
			String assigningAuthority) throws MpiClientException {

		Map<String, String> queryParameters = new HashMap<String, String>();
		queryParameters.put("@PID.3.1", identifier);
		
		if(assigningAuthority.matches("^(\\d+?\\.){1,}\\d+$")) {
			queryParameters.put("@PID.3.4.2", assigningAuthority);
			queryParameters.put("@PID.3.4.3", "ISO");
		}
		else 
			queryParameters.put("@PID.3.4.1", assigningAuthority);

		// Auditing stuff
		AuditMessage auditMessage = null;
		Message request = null;
		
		try
		{
			request = this.m_messageUtil.createPdqMessage(queryParameters);
			Message response = this.m_messageUtil.sendMessage(request, this.m_configuration.getPdqEndpoint(), this.m_configuration.getPdqPort());
			
			Terser terser = new Terser(response);
			if(!terser.get("/MSA-1").endsWith("A"))
				throw new MpiClientException(String.format("Error retrieving data :> %s", terser.get("/MSA-1")), response);
			
			List<MpiPatient> pats = this.m_messageUtil.interpretPIDSegments(response);
			auditMessage = AuditUtil.getInstance().createPatientSearch(pats, this.m_configuration.getPdqEndpoint(), (QBP_Q21)request);

			if(pats.size() > 1)
				throw new DuplicateIdentifierException("More than one patient exists");
			else if(pats.size() == 0)
				return null;
			else
				return pats.get(0);
		}
		catch(MpiClientException e)
		{
			log.error("Error in PDQ Search", e);
			if(e.getResponseMessage() != null)
				try {
					log.error(new PipeParser().encode(e.getResponseMessage()));
				} catch (HL7Exception e1) {
					// TODO Auto-generated catch block
					e1.printStackTrace();
				}
			throw e;
		}
		catch(Exception e)
		{
			log.error("Error in PDQ Search", e);

			if(request != null)
				try {
					auditMessage = AuditUtil.getInstance().createPatientSearch(null, this.m_configuration.getPdqEndpoint(), (QBP_Q21)request);
				} catch (UnknownHostException e1) {
					// TODO Auto-generated catch block
					this.log.error("Error creating error audit:", e1);
				}

			throw new MpiClientException(e);
		}
		finally
		{
			if(auditMessage != null)
				try {
					this.getAuditLogger().write(Calendar.getInstance(), auditMessage);
				} catch (Exception e) {
					log.error(e);
				}
		}
	}

	/**
	 * Import the patient from the PDQ supplier
	 * @throws MpiClientException 
	 */
	public Patient importPatient(MpiPatient patient) throws MpiClientException 
	{
		Patient patientRecord = Context.getService(MpiClientService.class).matchWithExistingPatient(patient);
		
		// Existing? Then update this from that
		if(patientRecord != null)
		{
			this.log.info(String.format("Matched with %s updating", patientRecord.getId()));
			// Add new identifiers
			for(PatientIdentifier id : patient.getIdentifiers())
			{
				boolean hasId = false;
				if(id.getIdentifierType() == null) continue ;
				for(PatientIdentifier eid : patientRecord.getIdentifiers())
					hasId |= eid.getIdentifier().equals(id.getIdentifier()) && eid.getIdentifierType().getId().equals(id.getIdentifierType().getId());
				if(!hasId) {
					if(id.getIdentifierType().getLocationBehavior().equals(LocationBehavior.REQUIRED))
						id.setLocation(Context.getLocationService().getDefaultLocation());
					patientRecord.getIdentifiers().add(id);
				}
			}
			
			// update names
			patientRecord.getNames().clear();
			for(PersonName name : patient.getNames())
				patientRecord.addName(name);
			// update addr
			patientRecord.getAddresses().clear();
			for(PersonAddress addr : patient.getAddresses())
				patientRecord.addAddress(addr);
			
			// Update deceased
			patientRecord.setDead(patient.getDead());
			patientRecord.setDeathDate(patient.getDeathDate());
			patientRecord.setBirthdate(patient.getBirthdate());
			patientRecord.setBirthdateEstimated(patient.getBirthdateEstimated());
			patientRecord.setGender(patient.getGender());
			
		}
		else
		{
			boolean isPreferred = false;
			
			patientRecord = patient.toPatient();
			
			PatientIdentifier ecidPid = null;
			
			for(PatientIdentifier id : patientRecord.getIdentifiers()) {
				if(id.getIdentifierType() == null)
					ecidPid = id;
				isPreferred |= id.getPreferred();
			}
			patientRecord.removeIdentifier(ecidPid);
			
			if(!isPreferred)
				patientRecord.getIdentifiers().iterator().next().setPreferred(true);
			
		}
		
		Patient importedPatient = null;
		try {
			importedPatient = Context.getPatientService().savePatient(patientRecord);
		}
		catch(APIException e) {
			throw new MpiClientException("Unable to insert patient",e); 
		}
		// Now setup the relationships
		if(patient instanceof MpiPatient &&
				this.m_configuration.getUseOpenMRSRelationships()) {
			MpiPatient mpiPatient = (MpiPatient)patient;

			// Insert relationships
			for(Relationship rel : mpiPatient.getRelationships()) {
				Context.getPersonService().saveRelationship(new Relationship(importedPatient, rel.getPersonB(), rel.getRelationshipType()));
			}
		}
		
		// Now notify the MPI
		MpiPatientExport mpiPatientExport = new MpiPatientExport(importedPatient,null, null,null,null);
		this.exportPatient(mpiPatientExport);
		return importedPatient;
	}
	
	/**
	 * Export a patient to the HIE
	 * @throws MpiClientException 
	 * @throws HL7Exception 
	 */
	public void exportPatient(MpiPatientExport patientExport) throws MpiClientException {
		// TODO Auto-generated method stub
		
		Message admitMessage = null;
		AuditMessage auditMessage = null;

		try
		{
		
			admitMessage = this.m_messageUtil.createAdmit(patientExport.getPatient());
			auditMessage = AuditUtil.getInstance().createPatientAdmit(patientExport.getPatient(), this.m_configuration.getPixEndpoint(), admitMessage, true);
			
			if(this.m_configuration.getUseBackgroundThreads())
			{
				MessageDispatchWorker worker = new MessageDispatchWorker(admitMessage, auditMessage, this.m_configuration.getPixEndpoint(), this.m_configuration.getPixPort());
				worker.start();
				auditMessage = null; // prevent sending
			}
			else {
				Message response = this.m_messageUtil.sendMessage(admitMessage, this.m_configuration.getPixEndpoint(), this.m_configuration.getPixPort());
			
				Terser terser = new Terser(response);
				log.info(String.format("Message indicates: %s", terser.get("/MSA-1")));
				if(!terser.get("/MSA-1").endsWith("A"))
					throw new MpiClientException(String.format("Error from MPI :> %s", terser.get("/MSA-1")), response);
			}			

		}
		catch(MpiClientException e)
		{
			log.error("Error in PIX message", e);
			if(e.getResponseMessage() != null) {
				try {
					log.error(new PipeParser().encode(e.getResponseMessage()));
					ACK ack = ((ACK)e.getResponseMessage());
					for(ELD erd : ack.getERR().getErrorCodeAndLocation())
					{
						log.error(String.format("MPI Error: %s : %s", erd.getCodeIdentifyingError().getIdentifier().getValue(), erd.getCodeIdentifyingError().getText().getValue()));
					}
					throw new MpiClientException(ack.getERR().getErrorCodeAndLocation(0).getCodeIdentifyingError().getText().getValue(), e);
				} catch (HL7Exception e1) {
					// TODO Auto-generated catch block
					e1.printStackTrace();
				}
			}
			throw e;
		}
		catch(Exception e)
		{
			e.printStackTrace();
			log.error(e);
			if(auditMessage != null)
				auditMessage = AuditUtil.getInstance().createPatientAdmit(patientExport.getPatient(), this.m_configuration.getPixEndpoint(), admitMessage, false);

			throw new MpiClientException(e);
		}
		finally
		{
			if(auditMessage != null)
				try
				{
					this.getAuditLogger().write(Calendar.getInstance(), auditMessage);
				}
				catch(Exception e)
				{
					log.error(e);
				}
		}	

	}

	/**
	 * Resolve patient identifier of the patient
	 * @throws MpiClientException 
	 */
	public PatientIdentifier resolvePatientIdentifier(Patient patient,
			String toAssigningAuthority) throws MpiClientException {
		
		AuditMessage auditMessage = null;

		Message request = null;
		try
		{
			request = this.m_messageUtil.createPixMessage(patient, toAssigningAuthority);
			Message response = this.m_messageUtil.sendMessage(request, this.m_configuration.getPixEndpoint(), this.m_configuration.getPixPort());
			
			// Interpret the result
			List<MpiPatient> candidate = this.m_messageUtil.interpretPIDSegments(response);
			auditMessage = AuditUtil.getInstance().createPatientResolve(candidate, this.m_configuration.getPixEndpoint(), request);
			if(candidate.size() == 0)
				return null;
			else
				return candidate.get(0).getIdentifiers().iterator().next();
		}
		catch(Exception e)
		{
			log.error(e);
			if(request != null)
				auditMessage = AuditUtil.getInstance().createPatientResolve(null, this.m_configuration.getPixEndpoint(), request);

			throw new MpiClientException(e);
		}
		finally
		{
			if(auditMessage != null)
				try
				{
					this.getAuditLogger().write(Calendar.getInstance(), auditMessage);
				}
				catch(Exception e)
				{
					log.error(e);
				}
		}
	}

	
	/**
	 * Update the patient record
	 * @see org.openmrs.module.santedb.mpiclient.api.MpiClientService#updatePatient(org.openmrs.Patient)
	 */
	public void updatePatient(Patient patient) throws MpiClientException {
		
		// TODO Auto-generated method stub
		AuditMessage auditMessage = null;

		Message admitMessage = null;
		try
		{
			admitMessage = this.m_messageUtil.createUpdate(patient);
			Message	response = this.m_messageUtil.sendMessage(admitMessage, this.m_configuration.getPixEndpoint(), this.m_configuration.getPixPort());
			
			Terser terser = new Terser(response);
			if(!terser.get("/MSA-1").endsWith("A"))
				throw new MpiClientException(String.format("Error querying data :> %s", terser.get("/MSA-1")), response);
			auditMessage = AuditUtil.getInstance().createPatientAdmit(patient, this.m_configuration.getPixEndpoint(), admitMessage, true);


		}
		catch(MpiClientException e)
		{
			log.error("Error in PIX message", e);
			if(e.getResponseMessage() != null)
				try {
					log.error(new PipeParser().encode(e.getResponseMessage()));
				} catch (HL7Exception e1) {
					// TODO Auto-generated catch block
					e1.printStackTrace();
				}
			throw e;
		}
		catch(Exception e)
		{
			log.error(e);
			if(auditMessage != null)
				auditMessage = AuditUtil.getInstance().createPatientAdmit(patient, this.m_configuration.getPixEndpoint(), admitMessage, false);

			throw new MpiClientException(e);
		}
		finally
		{
			if(auditMessage != null)
				try
				{
					this.getAuditLogger().write(Calendar.getInstance(), auditMessage);
				}
				catch(Exception e)
				{
					log.error(e);
				}
		}	
		
    }
	
	/**
	 * Get Audit logger
	 */
	public AuditLogger getAuditLogger() {
		if(this.m_logger == null)
		{
			synchronized (this.m_lockObject) {
	            if(this.m_logger == null)
	            {
            		this.m_configuration = MpiClientConfiguration.getInstance();	
            		this.m_logger = AuditUtil.getInstance().createLoggerDevice().getDeviceExtension(AuditLogger.class);
	            }
            }
		}
		return this.m_logger;
	}

	
}
