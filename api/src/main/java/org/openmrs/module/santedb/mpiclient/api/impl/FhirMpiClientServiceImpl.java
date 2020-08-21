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

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Date;
import java.util.List;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.rest.api.EncodingEnum;
import ca.uhn.fhir.rest.api.MethodOutcome;
import ca.uhn.fhir.rest.client.api.IGenericClient;
import ca.uhn.fhir.rest.client.api.ServerValidationModeEnum;
import ca.uhn.fhir.rest.client.interceptor.BasicAuthInterceptor;
import ca.uhn.fhir.rest.client.interceptor.BearerTokenAuthInterceptor;
import ca.uhn.fhir.rest.gclient.IQuery;
import com.google.common.io.CharStreams;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.dcm4che3.net.audit.AuditLogger;
import org.hl7.fhir.instance.model.api.IBaseBundle;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Bundle.BundleEntryComponent;
import org.hl7.fhir.r4.model.Patient.PatientLinkComponent;
import org.hl7.fhir.r4.model.codesystems.LinkType;
import org.openmrs.Patient;
import org.openmrs.PatientIdentifier;
import org.openmrs.module.fhir2.api.translators.PatientTranslator;
import org.openmrs.module.santedb.mpiclient.api.MpiClientWorker;
import org.openmrs.module.santedb.mpiclient.configuration.MpiClientConfiguration;
import org.openmrs.module.santedb.mpiclient.exception.MpiClientException;
import org.openmrs.module.santedb.mpiclient.model.MpiPatient;
import org.openmrs.module.santedb.mpiclient.util.FhirUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

/**
 * MPI Client Service Implementation using FHIR
 * 
 * @author fyfej
 *
 */
@Component
public class FhirMpiClientServiceImpl implements MpiClientWorker {

	// Lock object
	private Object m_lockObject = new Object();
	// Audit logger
	protected AuditLogger m_logger = null;
	// Log
	private static Log log = LogFactory.getLog(HL7MpiClientServiceImpl.class);
	// Message utility
	private FhirUtil m_messageUtil = FhirUtil.getInstance();

	// Get health information exchange information
	private MpiClientConfiguration m_configuration = MpiClientConfiguration.getInstance();

	@Autowired
	@Qualifier("fhirR4")
	private FhirContext ctx;

	@Autowired
	private PatientTranslator patientTranslator;

	/**
	 * Get the client as configured in this copy of the OMOD
	 */
	// TODO: use existing FHIR context

	private IGenericClient getClient(boolean isSearch) throws MpiClientException {
		FhirContext ctx = FhirContext.forR4();

		if(null != this.m_configuration.getProxy() && !this.m_configuration.getProxy().isEmpty())
		{
			String[] proxyData = this.m_configuration.getProxy().split(":");
			ctx.getRestfulClientFactory().setProxy(proxyData[0], Integer.parseInt(proxyData[1]));
		}
		
		IGenericClient client = ctx.newRestfulGenericClient(isSearch ?
				this.m_configuration.getPdqEndpoint() :
				this.m_configuration.getPixEndpoint());

		client.setEncoding(EncodingEnum.JSON);
		ctx.getRestfulClientFactory().setServerValidationMode(ServerValidationModeEnum.NEVER);

		// Is an IDP provided?
		if (this.m_configuration.getIdentityProviderUrl() != null
				&& !this.m_configuration.getIdentityProviderUrl().isEmpty()
				&& "oauth".equals(this.m_configuration.getAuthenticationMode())) {

			// Call the IDP
			CloseableHttpClient oauthClientCredentialsClient = HttpClientBuilder.create().build();

			try {
				HttpPost post = new HttpPost(this.m_configuration.getIdentityProviderUrl());
				post.addHeader("Content-Type", "application/x-www-form-urlencoded");

				// HACK: SanteMPI requires either X.509 node authentication (configured via JKS)
				// but can also use the X-Device-Authorization header
				// Since the JKS / X.509 node authentication is not supported, we'll have to use
				// the X-DeviceAuthorization
				String clientSecret = this.m_configuration.getMsh8Security(), deviceSecret = null;
				if (clientSecret.contains("+")) {
					String[] clientParts = clientSecret.split("\\+");
					clientSecret = clientParts[1];
					deviceSecret = clientParts[0];

					// Now append the proper header for device authentication
					post.addHeader("X-Device-Authorization",
							String.format("basic %s",
									Base64.getEncoder()
											.encodeToString(String
													.format("%s|%s:%s", this.m_configuration.getLocalApplication(),
															this.m_configuration.getLocalFacility(), deviceSecret)
													.getBytes())));
				}

				post.setEntity(new StringEntity(
						String.format("client_id=%s&client_secret=%s&grant_type=client_credentials&socpe=*",
								this.m_configuration.getLocalApplication(), clientSecret)));

				HttpResponse response = oauthClientCredentialsClient.execute(post);
				if (response.getStatusLine().getStatusCode() == HttpStatus.SC_OK) {
					InputStream inStream = response.getEntity().getContent();
					try {
						Reader reader = new InputStreamReader(inStream);
						String jsonText = CharStreams.toString(reader);
						JsonParser parser = new JsonParser();
						JsonObject oauthResponse = parser.parse(jsonText).getAsJsonObject();
						String token = oauthResponse.get("access_token").getAsString();
						log.warn(String.format("Using token: %s", token));
						client.registerInterceptor(new BearerTokenAuthInterceptor(token));

					} finally {
						inStream.close();
					}
				} else
					throw new Exception(String.format("Identity provider responded with %s",
							response.getStatusLine().getStatusCode()));

			} catch (Exception e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
				throw new MpiClientException(
						String.format("Could not authenticate client %s", this.m_configuration.getLocalApplication()),
						e);
			} finally {
				try {
					oauthClientCredentialsClient.close();
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
		}
		// Basic Auth
		else if("basic".equals(this.m_configuration.getAuthenticationMode())) {
			client.registerInterceptor(new BasicAuthInterceptor(this.m_configuration.getLocalApplication(), this.m_configuration.getMsh8Security()));
		}
		return client;
	}

	/**
	 * Search for patients in the MPI
	 */
	@Override
	public List<MpiPatient> searchPatient(String familyName, String givenName, Date dateOfBirth, boolean fuzzyDate,
			String gender, String stateOrRegion, String cityOrTownship, PatientIdentifier patientIdentifier,
			PatientIdentifier mothersIdentifier, String nextOfKinName, String birthPlace) throws MpiClientException {

		IQuery<IBaseBundle> query = this.getClient(true).search().forResource("Patient");

		if (familyName != null && !familyName.isEmpty())
			query = query.where(org.hl7.fhir.r4.model.Patient.FAMILY.contains().value(familyName));
		if (givenName != null && !givenName.isEmpty())
			query = query.where(org.hl7.fhir.r4.model.Patient.GIVEN.contains().value(givenName));
		if (dateOfBirth != null) {
			if (fuzzyDate) {
				if (this.m_configuration.getPdqDateFuzz() == 0) {
					query = query.where(
							org.hl7.fhir.r4.model.Patient.BIRTHDATE.after().day(new Date(dateOfBirth.getYear(), 0, 1)));
					query = query.where(org.hl7.fhir.r4.model.Patient.BIRTHDATE.before()
							.day(new Date(dateOfBirth.getYear(), 11, 31)));
				} else {
					query = query.where(org.hl7.fhir.r4.model.Patient.BIRTHDATE.after()
							.day(new Date(dateOfBirth.getYear() - this.m_configuration.getPdqDateFuzz(), 0, 1)));
					query = query.where(org.hl7.fhir.r4.model.Patient.BIRTHDATE.before()
							.day(new Date(dateOfBirth.getYear() + this.m_configuration.getPdqDateFuzz(), 11, 31)));
				}
			} else
				query = query.where(org.hl7.fhir.r4.model.Patient.BIRTHDATE.exactly().day(dateOfBirth));
		}

		if (gender != null && !gender.isEmpty())
			query = query.where(org.hl7.fhir.r4.model.Patient.GENDER.exactly().code(gender));

		if (patientIdentifier != null) {
			if (patientIdentifier.getIdentifierType() != null) {
				String authority = this.m_configuration.getLocalPatientIdentifierTypeMap()
						.get(patientIdentifier.getIdentifierType().getName());
				if (authority == null)
					throw new MpiClientException(
							String.format("Identity domain %s doesn't have an equivalent in the MPI configuration",
									patientIdentifier.getIdentifierType().getName()));
				query = query.where(org.hl7.fhir.r4.model.Patient.IDENTIFIER.exactly()
						.systemAndIdentifier(patientIdentifier.getIdentifier(), authority));
			} else
				query = query.where(org.hl7.fhir.r4.model.Patient.IDENTIFIER.exactly()
						.identifier(patientIdentifier.getIdentifier()));

		}

		if (stateOrRegion != null && !stateOrRegion.isEmpty())
			query = query.where(org.hl7.fhir.r4.model.Patient.ADDRESS_STATE.contains().value(stateOrRegion));
		if (cityOrTownship != null && !cityOrTownship.isEmpty())
			query = query.where(org.hl7.fhir.r4.model.Patient.ADDRESS_CITY.contains().value(cityOrTownship));

		// Send the message and construct the result set
		try {

			Bundle results = query.returnBundle(Bundle.class).execute();

			List<MpiPatient> retVal = new ArrayList<MpiPatient>();
			for (BundleEntryComponent result : results.getEntry()) {
				org.hl7.fhir.r4.model.Patient pat = (org.hl7.fhir.r4.model.Patient) result.getResource();

				// TODO: Create module-specific translator: Fhir.Patient <--> MpiPatient
				retVal.add((MpiPatient)patientTranslator.toOpenmrsType(pat));
			}
			return retVal;
		} catch (Exception e) {
			e.printStackTrace();
			log.error("Error in PDQ Search", e);
			throw new MpiClientException(e);
		} finally {
		}
	}

	/**
	 * Retrieves a specific patient from the MPI given their identifier
	 */
	@Override
	public MpiPatient getPatient(String identifier, String assigningAuthority) throws MpiClientException {

		// Send the message and construct the result set
		try {

			Bundle results = this
					.getClient(true).search().forResource("Patient").where(org.hl7.fhir.r4.model.Patient.IDENTIFIER
							.exactly().systemAndIdentifier(assigningAuthority, identifier))
					.count(1).returnBundle(Bundle.class).execute();

			List<MpiPatient> retVal = new ArrayList<MpiPatient>();
			for (BundleEntryComponent result : results.getEntry()) {
				
				org.hl7.fhir.r4.model.Patient pat = (org.hl7.fhir.r4.model.Patient) result.getResource();
				// TODO: use fhir2 translator to translate this
				return this.m_messageUtil.parseFhirPatient(pat);
			}
			return null; // no results
		} catch (Exception e) {
			e.printStackTrace();
			log.error("Error in PDQ Search", e);
			throw new MpiClientException(e);
		} finally {
		}
	}

	/**
	 * Resolve patient identifier in the specified identity domain
	 */
	@Override
	public PatientIdentifier resolvePatientIdentifier(Patient patient, String toAssigningAuthority)
			throws MpiClientException {
		// Send the message and construct the result set
		try {

			String identifier = null, assigningAuthority = null;
			// Preferred correlation identifier
			if (!this.m_configuration.getPreferredCorrelationDomain().isEmpty()) {
				for(PatientIdentifier pid : patient.getIdentifiers())
				{
					String domain = this.m_configuration.getLocalPatientIdentifierTypeMap().get(pid.getIdentifierType().getName());
					if(this.m_configuration.getPreferredCorrelationDomain().equals(domain))
					{
						identifier = pid.getIdentifier();
						assigningAuthority = domain;
						break;
					}
				}
			}
			else // use local identity
			{
				identifier = patient.getId().toString();
				assigningAuthority = this.m_configuration.getLocalPatientIdRoot();
			}
			
			// No identity domains to xref with
			if(identifier == null || assigningAuthority == null)
			{
				log.warn(String.format("Patient %s has no good cross reference identities to use", patient.getId()));
				return null;
			}
			
			
			Bundle results = this
					.getClient(true).search().forResource("Patient").where(org.hl7.fhir.r4.model.Patient.IDENTIFIER
							.exactly().systemAndIdentifier(assigningAuthority, identifier))
					.count(2).returnBundle(Bundle.class).execute();

			// ASSERT: Only 1 result
			if(results.getEntry().size() != 1)
				throw new MpiClientException(String.format("Found ambiguous matches (%s matches) on MPI, can't reliably xref this patient", results.getTotal()));
			
			// Is there a result?
			for (BundleEntryComponent result : results.getEntry()) {
				org.hl7.fhir.r4.model.Patient pat = (org.hl7.fhir.r4.model.Patient) result.getResource();
				
				// Is this patient linked to another patient?
				if(pat.getLink() != null)
					for(PatientLinkComponent lnk : pat.getLink())
					{
						if(LinkType.REFER.equals(lnk.getType())) {
							pat = (org.hl7.fhir.r4.model.Patient)lnk.getOtherTarget();
						}
					}

				MpiPatient mpiPatient = (MpiPatient)patientTranslator.toOpenmrsType(pat);

				// Now look for the identity domain we want to xref to
				for(PatientIdentifier pid : mpiPatient.getIdentifiers())
				{
					String domain = this.m_configuration.getLocalPatientIdentifierTypeMap().get(pid.getIdentifierType().getName());
					if(toAssigningAuthority.equals(domain))
						return pid;
				}
				return null;
			}
			return null; // no results
		} catch (Exception e) {
			e.printStackTrace();
			log.error("Error in PDQ Search", e);
			throw new MpiClientException(e);
		} finally {
		}
	}

	/**
	 * Imports patient from MPI? Was in not implemented due to lack of FHIR create capabilites in OpenMRS?
	 * @param patient
	 * @return
	 * @throws MpiClientException
	 */
	@Override
	public Patient importPatient(MpiPatient patient) throws MpiClientException {
		// TODO Auto-generated method stub
		return null;
	}

	
	/**
	 * Sends a patient to the MPI in FHIR format
	 */
	@Override
	public void exportPatient(Patient patient) throws MpiClientException {

		org.hl7.fhir.r4.model.Patient admitMessage = null;

		try {
			admitMessage = patientTranslator.toFhirResource(patient);

			IGenericClient client = this.getClient(false);
			MethodOutcome result = client.create().resource(admitMessage).execute();
			if (!result.getCreated())
				throw new MpiClientException(
						String.format("Error from MPI :> %s", result.getResource().getClass().getName()));
			
		} catch (MpiClientException e) {
			log.error("Error in FHIR PIX message", e);
			e.printStackTrace();
			throw e;
		} catch (Exception e) {
			e.printStackTrace();
			log.error(e);
			throw new MpiClientException(e);
		} finally {
		}

	}

	/**
	 * Updates the MPI patient information with local patient information
	 */
	@Override
	public void updatePatient(Patient patient) throws MpiClientException {
		org.hl7.fhir.r4.model.Patient admitMessage = null;

		try {
			admitMessage = patientTranslator.toFhirResource(patient);

			IGenericClient client = this.getClient(false);
			MethodOutcome result = client.update().resource(admitMessage).execute();
			if (!result.getCreated())
				throw new MpiClientException(
						String.format("Error from MPI :> %s", result.getResource().getClass().getName()));
		} catch (MpiClientException e) {
			log.error("Error in FHIR PIX message", e);
			e.printStackTrace();
			throw e;
		} catch (Exception e) {
			e.printStackTrace();
			log.error(e);
			throw new MpiClientException(e);
		} finally {
		}
	}

	@Override
	public AuditLogger getAuditLogger() {
		// TODO Auto-generated method stub
		return null;
	}
}
