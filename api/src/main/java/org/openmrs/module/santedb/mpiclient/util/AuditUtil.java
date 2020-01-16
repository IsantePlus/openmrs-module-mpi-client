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
package org.openmrs.module.santedb.mpiclient.util;

import java.lang.management.ManagementFactory;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.dcm4che3.audit.ActiveParticipant;
import org.dcm4che3.audit.AuditMessage;
import org.dcm4che3.audit.AuditMessages;
import org.dcm4che3.audit.AuditMessages.AuditSourceTypeCode;
import org.dcm4che3.audit.AuditMessages.EventID;
import org.dcm4che3.audit.AuditMessages.EventTypeCode;
import org.dcm4che3.audit.AuditMessages.ParticipantObjectIDTypeCode;
import org.dcm4che3.audit.AuditMessages.RoleIDCode;
import org.dcm4che3.net.Connection;
import org.dcm4che3.net.Device;
import org.dcm4che3.net.audit.AuditLogger;
import org.dcm4che3.net.audit.AuditRecordRepository;
import org.openmrs.ImplementationId;
import org.openmrs.Location;
import org.openmrs.Patient;
import org.openmrs.Role;
import org.openmrs.User;
import org.openmrs.api.context.Context;
import org.openmrs.module.santedb.mpiclient.configuration.MpiClientConfiguration;
import org.openmrs.module.santedb.mpiclient.exception.MpiClientException;
import org.openmrs.module.santedb.mpiclient.model.MpiPatient;

import ca.uhn.hl7v2.HL7Exception;
import ca.uhn.hl7v2.model.Message;
import ca.uhn.hl7v2.model.v25.message.QBP_Q21;
import ca.uhn.hl7v2.parser.PipeParser;
import ca.uhn.hl7v2.util.Terser;

/**
 * Audit utility for client audits
 * @author JustinFyfe
 *
 */
public class AuditUtil {

	
	// Instance of the audit utility
	private static AuditUtil s_instance;
	private static Object s_lock = new Object();
	private MpiClientConfiguration m_configuration = MpiClientConfiguration.getInstance();
	private Log log = LogFactory.getLog(this.getClass());

	/**
	 * Audit utility ctor
	 */
	private AuditUtil() {
		
	}
	
	/**
	 * Get the instance of the audit utility
	 * @return
	 */
	public static AuditUtil getInstance() {
		if(s_instance == null)
			synchronized(s_lock)
			{
				if(s_instance == null)
				{
					s_instance = new AuditUtil();
				}
			}
		return s_instance;
	}
	
	/**
	 * Create logger device
	 */
	public Device createLoggerDevice() { 
		Device device = new Device(String.format("%s^^^%s", this.m_configuration.getLocalApplication(), this.m_configuration.getLocalFacility()));

		
		Connection transportConnection = new Connection(this.m_configuration.getAuditRepositoryTransport(), this.m_configuration.getAuditRepositoryEndpoint());
		
		// UDP
		if("audit-udp".equals(transportConnection.getCommonName()))
		{
			if(this.m_configuration.getAuditRepositoryBindAddress().isEmpty())
				transportConnection.setClientBindAddress(null);
			else 
				transportConnection.setClientBindAddress(this.m_configuration.getAuditRepositoryBindAddress());
			transportConnection.setProtocol(Connection.Protocol.SYSLOG_UDP);
		}
		else if("audit-tcp".equals(transportConnection.getCommonName()))
		{
			transportConnection.setProtocol(Connection.Protocol.DICOM);
		}
		else if("audit-tls".equals(transportConnection.getCommonName()))
		{
			transportConnection.setProtocol(Connection.Protocol.SYSLOG_TLS);
			transportConnection.setTlsCipherSuites("TLS_RSA_WITH_AES_128_CBC_SHA");
		}
		else
			throw new IllegalArgumentException("Connection must be audit-tls or audit-udp");

		transportConnection.setPort(this.m_configuration.getAuditRepositoryPort());
		
		device.addConnection(transportConnection);
		
		AuditRecordRepository repository = new AuditRecordRepository();
		device.addDeviceExtension(repository);
		repository.addConnection(transportConnection);

		AuditLogger logger = new AuditLogger();
		device.addDeviceExtension(logger);
		logger.addConnection(transportConnection);
		logger.setAuditRecordRepositoryDevice(device);
		logger.setSchemaURI(AuditMessages.SCHEMA_URI);
		
		return device;
		
	}
	
	/**
	 * Create audit message
	 * @return
	 */
	private AuditMessage createAuditMessageQuery(EventTypeCode eventType, boolean success)
	{
		AuditMessage retVal = new AuditMessage();
		retVal.setEventIdentification(AuditMessages.createEventIdentification(EventID.Query, "E", Calendar.getInstance(), success ? "0" : "12", success ? "Success" : "Failure", eventType));
		
		ImplementationId implementation = Context.getAdministrationService().getImplementationId();

		if(implementation == null)
		{
			implementation = new ImplementationId();
			implementation.setName("ANON");
			implementation.setImplementationId("ANON");
		}

		Location defaultLocation = Context.getLocationService().getDefaultLocation();
		
		retVal.getAuditSourceIdentification().add(AuditMessages.createAuditSourceIdentification(defaultLocation.getName(), implementation.getImplementationId(), AuditSourceTypeCode.WebServerProcess));
		return retVal;
	}

	/**
	 * Create the human requestor
	 * @return
	 */
	private ActiveParticipant createHumanRequestor()
	{
		User currentUser = Context.getAuthenticatedUser();
		ImplementationId implementation = Context.getAdministrationService().getImplementationId();
		if(implementation == null)
		{
			implementation = new ImplementationId();
			implementation.setName("ANON");
			implementation.setImplementationId("ANON");
		}

		String altUserId = String.format("%s\\%s", implementation.getImplementationId(), currentUser.getName()),
				userName = String.format("%s, %s", currentUser.getFamilyName(), currentUser.getGivenName());
		List<RoleIDCode> roles = new ArrayList<AuditMessages.RoleIDCode>();
		for(Role rol : currentUser.getAllRoles())
			roles.add(new RoleIDCode(rol.getName(), null, null));
		
		return AuditMessages.createActiveParticipant(currentUser.getUsername(), altUserId, userName, true, null, null, null, roles.toArray(new RoleIDCode[] {}));
	}
	
	/**
	 * Create source participant
	 * @return
	 * @throws UnknownHostException 
	 */
	private ActiveParticipant createSourceParticipant(String userId) throws UnknownHostException {
		return AuditMessages.createActiveParticipant(
				userId, 
				ManagementFactory.getRuntimeMXBean().getName(), 
				null, 
				true, 
				InetAddress.getLocalHost().toString(), 
				"2", 
				AuditMessages.MediaType.URI, 
				RoleIDCode.Source
			) ;
	}
	
	/**
	 * Create patient search 
	 * @return
	 * @throws UnknownHostException 
	 */
	public AuditMessage createPatientSearch(List<MpiPatient> results, String remoteHost, QBP_Q21 query) throws UnknownHostException
	{
		AuditMessage retVal =  this.createAuditMessageQuery(EventTypeCode.ITI_21_PatientDemographicsQuery, results != null && results.size() > 0);
		retVal.getActiveParticipant().add(this.createHumanRequestor());
		retVal.getActiveParticipant().add(this.createSourceParticipant(String.format("%s|%s", query.getMSH().getSendingApplication().getNamespaceID(), query.getMSH().getSendingFacility().getNamespaceID())));
		retVal.getActiveParticipant().add(AuditMessages.createActiveParticipant(String.format("%s|%s", query.getMSH().getReceivingApplication().getNamespaceID(), query.getMSH().getReceivingFacility().getNamespaceID()), null, null, false, remoteHost, "1", null, AuditMessages.RoleIDCode.Destination));
		
		// Add objects
		PipeParser parser = new PipeParser();
		Terser terser = new Terser(query);
		try {
			retVal.getParticipantObjectIdentification().add(
					AuditMessages.createParticipantObjectIdentification(
							query.getMSH().getMessageControlID().getValue(), 
							new ParticipantObjectIDTypeCode("ITI-21", "IHE Transactions", "Patient Demographics Query"), 
							null, 
							parser.encode(query).getBytes(), 
							"2", 
							"24", 
							null, 
							null, 
							null, 
							AuditMessages.createParticipantObjectDetail("MSH-10", query.getMSH().getMessageControlID().getValue().getBytes())
						));
		} catch (HL7Exception e) {
			log.error("Error constructing query:", e);
		}
		
		// Results
		if(results != null)
			for(Patient res : results)
			{
				if(res == null ||
						res.getPatientIdentifier() == null)
					continue;
				
				String domain = null;
				if(res.getPatientIdentifier().getIdentifierType() != null)
					domain = this.m_configuration.getLocalPatientIdentifierTypeMap().get(res.getPatientIdentifier().getIdentifierType().getName());
				else 
					domain = this.m_configuration.getEnterprisePatientIdRoot();
				
				retVal.getParticipantObjectIdentification().add(
					AuditMessages.createParticipantObjectIdentification(
							String.format("%s^^^&%s&ISO", res.getPatientIdentifier().getIdentifier(), domain), 
							ParticipantObjectIDTypeCode.PatientNumber, 
							null, 
							null, 
							"1", 
							"1", 
							null, 
							null, 
							null, 
							AuditMessages.createParticipantObjectDetail("MSH-10", query.getMSH().getMessageControlID().getValue().getBytes())	
					)
				);
				
			}
		
		return retVal;
	}

	/**
	 * Create patient search 
	 * @return
	 * @throws MpiClientException 
	 */
	public AuditMessage createPatientResolve(List<MpiPatient> results, String remoteHost, Message query) throws MpiClientException
	{
		try
		{
			Terser terser = new Terser(query);
			AuditMessage retVal =  this.createAuditMessageQuery(EventTypeCode.ITI_9_PIXQuery, results != null && results.size() > 0);
			retVal.getActiveParticipant().add(this.createHumanRequestor());
			retVal.getActiveParticipant().add(this.createSourceParticipant(String.format("%s|%s", terser.get("/MSH-3"), terser.get("/MSH-4"))));
			retVal.getActiveParticipant().add(AuditMessages.createActiveParticipant(String.format("%s|%s", terser.get("/MSH-5"), terser.get("/MSH-6")), null, null, false, remoteHost, "1", null, AuditMessages.RoleIDCode.Destination));
			
			// Add objects
			PipeParser parser = new PipeParser();
			try {
				retVal.getParticipantObjectIdentification().add(
						AuditMessages.createParticipantObjectIdentification(
								terser.get("/MSH-10"), 
								new ParticipantObjectIDTypeCode("ITI-9", "IHE Transactions", "PIX Query"), 
								null, 
								parser.encode(query).getBytes(), 
								"2", 
								"24", 
								null, 
								null, 
								null, 
								AuditMessages.createParticipantObjectDetail("MSH-10", terser.get("/MSH-10").getBytes())
							));
			} catch (HL7Exception e) {
				log.error("Error constructing query:", e);
			}
			
			// Results
			if(results != null)
				for(Patient res : results)
				{
					if(res == null ||
							res.getPatientIdentifier() == null)
						continue;
					
					String domain = null;
					if(res.getPatientIdentifier().getIdentifierType() != null)
						domain = this.m_configuration.getLocalPatientIdentifierTypeMap().get(res.getPatientIdentifier().getIdentifierType().getName());
					else 
						domain = this.m_configuration.getEnterprisePatientIdRoot();
					
					retVal.getParticipantObjectIdentification().add(
						AuditMessages.createParticipantObjectIdentification(
								String.format("%s^^^&%s&ISO", res.getPatientIdentifier().getIdentifier(), domain), 
								ParticipantObjectIDTypeCode.PatientNumber, 
								null, 
								null, 
								"1", 
								"1", 
								null, 
								null, 
								null
						)
					);
					
				}
			
			return retVal;
		}
		catch(Exception e)
		{
			log.error("Error creating audit", e);
			throw new MpiClientException("Error creating audit", e);
		}
	}

	/**
	 * Create patient search 
	 * @return
	 * @throws MpiClientException 
	 */
	public AuditMessage createPatientAdmit(Patient patient, String remoteHost, Message query, Boolean success) throws MpiClientException
	{
		try
		{
			Terser terser = new Terser(query);
			AuditMessage retVal =  this.createAuditMessageQuery(EventTypeCode.ITI_8_PatientIdentityFeed, success);
			if(terser.get("/MSH-9-2").equals("A08"))
				retVal.getEventIdentification().setEventActionCode("U");
			else
				retVal.getEventIdentification().setEventActionCode("C");
			
			retVal.getActiveParticipant().add(this.createHumanRequestor());
			retVal.getActiveParticipant().add(this.createSourceParticipant(String.format("%s|%s", terser.get("/MSH-3"), terser.get("/MSH-4"))));
			retVal.getActiveParticipant().add(AuditMessages.createActiveParticipant(String.format("%s|%s", terser.get("/MSH-5"), terser.get("/MSH-6")), null, null, false, remoteHost, "1", null, AuditMessages.RoleIDCode.Destination));
			
			if(patient != null)
				retVal.getParticipantObjectIdentification().add(
					AuditMessages.createParticipantObjectIdentification(
							this.m_configuration.getLocalPatientIdRoot().matches("^(\\\\d+?\\\\.){1,}\\\\d+$") ?
									String.format("%s^^^&%s&ISO", patient.getId(), this.m_configuration.getLocalPatientIdRoot()) :
									String.format("%s^^^%s", patient.getId(), this.m_configuration.getLocalPatientIdRoot()) 
										,
							ParticipantObjectIDTypeCode.PatientNumber, 
							null, 
							null, 
							"1", 
							"1", 
							null, 
							null, 
							null	
					)
				);
					
			
			return retVal;
		}
		catch(Exception e)
		{
			log.error("Error creating audit", e);
			throw new MpiClientException("Error creating audit", e);
		}
	}

}
