/**
 * Portions Copyright 2015-2018 Mohawk College of Applied Arts and Technology
 * Portions Copyright (c) 2014-2020 Fyfe Software Inc.
 * Licensed under the Apache License, Version 2.0 (the "License"); you
 * may not use this file except in compliance with the License. You may
 * obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0
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
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.rest.api.EncodingEnum;
import ca.uhn.fhir.rest.api.MethodOutcome;
import ca.uhn.fhir.rest.client.api.IGenericClient;
import ca.uhn.fhir.rest.client.api.ServerValidationModeEnum;
import ca.uhn.fhir.rest.client.exceptions.FhirClientConnectionException;
import ca.uhn.fhir.rest.client.interceptor.BasicAuthInterceptor;
import ca.uhn.fhir.rest.client.interceptor.BearerTokenAuthInterceptor;
import ca.uhn.fhir.rest.gclient.IQuery;
import com.google.common.io.CharStreams;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.exception.ExceptionUtils;
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
import org.hl7.fhir.r4.model.Address;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Bundle.BundleEntryComponent;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.ContactPoint;
import org.hl7.fhir.r4.model.Extension;
import org.hl7.fhir.r4.model.HumanName;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.Patient.PatientLinkComponent;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.StringType;
import org.hl7.fhir.r4.model.codesystems.LinkType;
import org.openmrs.Obs;
import org.openmrs.Patient;
import org.openmrs.PatientIdentifier;
import org.openmrs.PatientIdentifierType;
import org.openmrs.PatientIdentifierType.LocationBehavior;
import org.openmrs.PersonName;
import org.openmrs.PersonAddress;
import org.openmrs.Relationship;
import org.openmrs.Location;
import org.openmrs.api.APIException;
import org.openmrs.api.context.Context;
import org.openmrs.module.santedb.mpiclient.api.MpiClientService;
import org.openmrs.module.santedb.mpiclient.aop.PatientSynchronizationAdvice;
import org.openmrs.PersonAttribute;
import org.openmrs.module.fhir2.api.translators.PatientTranslator;
import org.openmrs.module.santedb.mpiclient.api.MpiClientWorker;
import org.openmrs.module.santedb.mpiclient.configuration.MpiClientConfiguration;
import org.openmrs.module.santedb.mpiclient.exception.MpiClientException;
import org.openmrs.module.santedb.mpiclient.model.MpiPatient;
import org.openmrs.module.santedb.mpiclient.model.MpiPatientExport;
import org.openmrs.module.santedb.mpiclient.util.FhirUtil;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Component;

/**
 * MPI Client Service Implementation using FHIR
 *
 * @author fyfej
 */
@Component
public class FhirMpiClientServiceImpl implements MpiClientWorker, ApplicationContextAware {

	// Lock object
	private final Object m_lockObject = new Object();

	// Audit logger
	protected AuditLogger m_logger = null;

	// Log
	private static final Log log = LogFactory.getLog(HL7MpiClientServiceImpl.class);

	// A bare UUID is never a valid site/person identifier value — it's an internal MPI id (golden
	// CRUID, ECID, or a source-record uuid). Such values must not be exported to the registry.
	private static final java.util.regex.Pattern UUID_VALUE =
	        java.util.regex.Pattern.compile("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");

	// Get health information exchange information
	private final MpiClientConfiguration m_configuration = MpiClientConfiguration.getInstance();

	private ApplicationContext applicationContext;

	@Autowired
	private PatientTranslator patientTranslator;

	@Autowired
	private FhirUtil fhirUtil;
	/**
	 * Get the client as configured in this copy of the OMOD
	 */
	// TODO: use existing FHIR context
	private IGenericClient getClient(boolean isSearch) throws MpiClientException {
		FhirContext ctx = FhirContext.forR4();
		if (null != this.m_configuration.getProxy() && !this.m_configuration.getProxy().isEmpty()) {
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
						String.format("client_id=%s&client_secret=%s&grant_type=client_credentials&scope=*",
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

					}
					finally {
						inStream.close();
					}
				} else
					throw new Exception(String.format("Identity provider responded with %s",
							response.getStatusLine().getStatusCode()));

			}
			catch (Exception e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
				throw new MpiClientException(
						String.format("Could not authenticate client %s", this.m_configuration.getLocalApplication()),
						e);
			}
			finally {
				try {
					oauthClientCredentialsClient.close();
				}
				catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
		}
		// Basic Auth
		else if ("basic".equals(this.m_configuration.getAuthenticationMode())) {
			client.registerInterceptor(new BasicAuthInterceptor(this.m_configuration.getLocalApplication(),
					this.m_configuration.getMsh8Security()));
		}
		return client;
	}

	/**
	 * Search for patients in the MPI
	 */
	@Override
	public List<MpiPatient> searchPatient(String familyName, String givenName, Date dateOfBirth, boolean fuzzyDate,
			String gender, String stateOrRegion, String cityOrTownship, PatientIdentifier patientIdentifier,
			PatientIdentifier mothersIdentifier, String nextOfKinName, String birthPlace,
			Map<String, Object> otherDataPoints) throws MpiClientException {
		Set<PatientIdentifier> patientIdentifiers = new HashSet<>();
		patientIdentifiers.add(patientIdentifier);

		return searchPatient(familyName, givenName, dateOfBirth, fuzzyDate, gender, stateOrRegion, cityOrTownship,
				patientIdentifiers, mothersIdentifier, nextOfKinName, birthPlace, otherDataPoints);
	}

	@Override
	public List<MpiPatient> searchPatient(String familyName, String givenName, Date dateOfBirth, boolean fuzzyDate,
			String gender,
			String stateOrRegion, String cityOrTownship, Set<PatientIdentifier> patientIdentifiers,
			PatientIdentifier mothersIdentifier, String nextOfKinName, String birthPlace,
			Map<String, Object> otherDataPoints) throws MpiClientException {
/*
		IQuery<IBaseBundle> query = loadSearchQuery(familyName, givenName, dateOfBirth, fuzzyDate, gender,
				stateOrRegion, cityOrTownship, patientIdentifiers, mothersIdentifier, nextOfKinName, birthPlace,
				otherDataPoints);
*/
		IQuery<IBaseBundle> query = loadSearchQuery(familyName, givenName, dateOfBirth, fuzzyDate, gender,
				null, null, null, null, null, null,otherDataPoints);		
		
		

		// Send the message and construct the result set
		return getMpiPatientMatches(query);
	}

	private List<MpiPatient> getMpiPatientMatches(IQuery<IBaseBundle> query) throws MpiClientException {
		try {

			Bundle results = query.returnBundle(Bundle.class).execute();
			
			log.warn(String.format("GetMpiPatientMatches ::: >>> "+ results.getEntry().size()));

			List<String> goldenRecordUuids = new ArrayList<>();
			List<MpiPatient> retVal = new ArrayList<>();
			Map<String, org.hl7.fhir.r4.model.Patient> patientMap = new HashMap<>();
			// First stage - loop through result set to get golden record uuids
			for (BundleEntryComponent result : results.getEntry()) {
				org.hl7.fhir.r4.model.Patient pat;
				if (result.hasResource() && result.getResource().hasType("Patient")) {
					pat = (org.hl7.fhir.r4.model.Patient) result.getResource();
					String grId = null;
					if (pat.hasLink() && pat.getLink().size() == 1 && pat.getLinkFirstRep().hasOther()) {
						grId = pat.getLinkFirstRep().getOther().getReferenceElement().getIdPart();
					}
					// Create a list with all of the unique IDs for the golden records.
					if (grId != null && !goldenRecordUuids.contains(grId))
						goldenRecordUuids.add(grId);
				}
			}

			if (!goldenRecordUuids.isEmpty()) {
				results = goldenRecordSetQuery(goldenRecordUuids).returnBundle(Bundle.class).execute();

				for (BundleEntryComponent result : results.getEntry()) {
					org.hl7.fhir.r4.model.Patient gr = (org.hl7.fhir.r4.model.Patient) result.getResource();
					if (gr != null && gr.hasType("Patient") && gr.hasMeta()
							&& gr.getMeta().hasTag()
							&& gr.getMeta().getTagFirstRep().hasCode()
							&& gr.getMeta().getTagFirstRep().getCode().equals(m_configuration.getGoldenRecordUuid())
							&& gr.hasLink()) {
						for (PatientLinkComponent grPatLink : gr.getLink()) {
							if (grPatLink.getOther().getResource() != null) {
								// Translate each linked source patient in isolation: a single
								// untranslatable record (e.g. a cross-facility identifier type not
								// present locally) must not fail the whole search result set.
								try {
									org.hl7.fhir.r4.model.Patient sourcePatient =
											(org.hl7.fhir.r4.model.Patient) grPatLink.getOther().getResource();
									MpiPatient mpiPatient = fhirUtil.parseFhirPatient(sourcePatient,
											patientTranslator.toOpenmrsType(sourcePatient));
									retVal.add(mpiPatient);
								} catch (Exception ex) {
									log.error("Skipping untranslatable MPI patient "
											+ grPatLink.getOther().getReference(), ex);
								}
							}
						}

					}
				}
			}
			return retVal;
		}
		catch (

				Exception e) {
			e.printStackTrace();
			log.error("Error in FHIR Search", e);
			log.error(ExceptionUtils.getFullStackTrace(e));
			throw new MpiClientException(e);
		}
		finally {
		}

	}

	private IQuery<IBaseBundle> loadSearchQuery(String familyName, String givenName, Date dateOfBirth, boolean fuzzyDate,
			String gender, String stateOrRegion, String cityOrTownship,
			Set<PatientIdentifier> patientIdentifiers,
			PatientIdentifier mothersIdentifier,
			String nextOfKinName, String birthPlace,
			Map<String, Object> otherDataPoints) throws MpiClientException {
		if (otherDataPoints == null) {
			otherDataPoints = new java.util.HashMap<String, Object>();
		}
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
		
		
		if(gender!=null) {
		if(gender.equals("M")) gender="male";
		if(gender.equals("F")) gender="female";
		}

		log.warn(String.format("GetMpiPatientMatches GENDER ::: >>> "+gender));
		
		if (gender != null && !gender.isEmpty())
			query = query.where(org.hl7.fhir.r4.model.Patient.GENDER.exactly().code(gender));

		if (patientIdentifiers != null) {
			for (PatientIdentifier patientIdentifier : patientIdentifiers) {
				if (patientIdentifier.getIdentifierType() != null) {

					HashMap<String, String> localPatientIdentifierTypeMap = this.m_configuration.getLocalPatientIdentifierTypeMap();
					String authority = localPatientIdentifierTypeMap
							.get(patientIdentifier.getIdentifierType().getName());
					if (authority == null)
						throw new MpiClientException(
								String.format("Identity domain %s doesn't have an equivalent in the MPI configuration",
										patientIdentifier.getIdentifierType().getName()));
					query = query.where(org.hl7.fhir.r4.model.Patient.IDENTIFIER.exactly()
							.systemAndIdentifier(patientIdentifier.getIdentifier(), authority));
				} else {
					//                    Skip the search by the identifier
				}
			}
		}
		String localBiometricSubjectId = (String) otherDataPoints.get("localBiometricSubjectId");
		String nationalBiometricSubjectId = (String) otherDataPoints.get("nationalBiometricSubjectId");
		String phoneNumber = (String) otherDataPoints.get("phoneNumber");

		if (StringUtils.isNotBlank(localBiometricSubjectId)) {
			query = query.where(org.hl7.fhir.r4.model.Patient.IDENTIFIER.exactly()
					.systemAndIdentifier(localBiometricSubjectId, "Biometrics Reference Code"));
		}
		if (StringUtils.isNotBlank(nationalBiometricSubjectId)) {
			query = query.where(org.hl7.fhir.r4.model.Patient.IDENTIFIER.exactly()
					.systemAndIdentifier(nationalBiometricSubjectId, "Biometrics National Reference Code"));
		}

		if (StringUtils.isNotBlank(phoneNumber)) {
			query = query.where(org.hl7.fhir.r4.model.Patient.PHONE.exactly().identifier(phoneNumber));
		}

		if (stateOrRegion != null && !stateOrRegion.isEmpty())
			query = query.where(org.hl7.fhir.r4.model.Patient.ADDRESS_STATE.contains().value(stateOrRegion));
		if (cityOrTownship != null && !cityOrTownship.isEmpty())
			query = query.where(org.hl7.fhir.r4.model.Patient.ADDRESS_CITY.contains().value(cityOrTownship));
		// query.include(new Include("Patient:link"));

		return query;
	}

	// Queries the Client Registry for a set of golden record uuids to get the records and associated Patients
	private IQuery<IBaseBundle> goldenRecordSetQuery(List<String> grUuids) throws MpiClientException {

		String uuidString = String.join(",", grUuids);

		IQuery<IBaseBundle> query = this.getClient(true).search()
				.byUrl("Patient?_id=" + uuidString + "&_include=Patient:link");

		return query;
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
							//                            .exactly().systemAndIdentifier(assigningAuthority, identifier))
							.exactly().identifier(identifier))
					.count(1).returnBundle(Bundle.class).execute();

			for (BundleEntryComponent result : results.getEntry()) {
				org.hl7.fhir.r4.model.Patient pat = (org.hl7.fhir.r4.model.Patient) result.getResource();
				MpiPatient mpiPatient = fhirUtil.parseFhirPatient(pat, patientTranslator.toOpenmrsType(pat));

				return mpiPatient;
			}
			return null; // no results
		}
		catch (Exception e) {
			e.printStackTrace();
			log.error("Error in PDQ Search", e);
			throw new MpiClientException(e);
		}
	}
/**
     * Retrieves a list of patient from the MPI given their identifier
     */
    @Override
    public List<MpiPatient> getPatientList(String identifier, String assigningAuthority) throws MpiClientException {

    	List<MpiPatient> mpiPatientList=new ArrayList<MpiPatient>();
        // Send the message and construct the result set
        try {
            Bundle results = this
                    .getClient(true).search().forResource("Patient").where(org.hl7.fhir.r4.model.Patient.IDENTIFIER
//                            .exactly().systemAndIdentifier(assigningAuthority, identifier))
                            .exactly().identifier(identifier))
                     .returnBundle(Bundle.class).execute();

            for (BundleEntryComponent result : results.getEntry()) {
                org.hl7.fhir.r4.model.Patient pat = (org.hl7.fhir.r4.model.Patient) result.getResource();
                MpiPatient mpiPatient = fhirUtil.parseFhirPatient(pat, patientTranslator.toOpenmrsType(pat));
                mpiPatientList.add(mpiPatient);
                
            }
            
            return mpiPatientList; // no results
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
				for (PatientIdentifier pid : patient.getIdentifiers()) {
					String domain = this.m_configuration.getLocalPatientIdentifierTypeMap()
							.get(pid.getIdentifierType().getName());
					if (this.m_configuration.getPreferredCorrelationDomain().equals(domain)) {
						identifier = pid.getIdentifier();
						assigningAuthority = domain;
						break;
					}
				}
			} else // use local identity
			{
				identifier = patient.getId().toString();
				assigningAuthority = this.m_configuration.getLocalPatientIdRoot();
			}

			// No identity domains to xref with
			if (identifier == null || assigningAuthority == null) {
				log.warn(String.format("Patient %s has no good cross reference identities to use", patient.getId()));
				return null;
			}

			Bundle results = this
					.getClient(true).search().forResource("Patient").where(org.hl7.fhir.r4.model.Patient.IDENTIFIER
							.exactly().systemAndIdentifier(assigningAuthority, identifier))
					.count(2).returnBundle(Bundle.class).execute();
			// ASSERT: Only 1 result
			if (results.getEntry().size() != 1)
				throw new MpiClientException(
						String.format("Found ambiguous matches (%s matches) on MPI, can't reliably xref this patient",
								results.getTotal()));

			// Is there a result?
			for (BundleEntryComponent result : results.getEntry()) {
				org.hl7.fhir.r4.model.Patient pat = (org.hl7.fhir.r4.model.Patient) result.getResource();

				// Is this patient linked to another patient?
				if (pat.getLink() != null)
					for (PatientLinkComponent lnk : pat.getLink()) {
						if (LinkType.REFER.equals(lnk.getType())) {
							pat = (org.hl7.fhir.r4.model.Patient) lnk.getOtherTarget();
						}
					}

				MpiPatient mpiPatient = fhirUtil.parseFhirPatient(pat, patientTranslator.toOpenmrsType(pat));

				// Now look for the identity domain we want to xref to
				for (PatientIdentifier pid : mpiPatient.getIdentifiers()) {
					String domain = this.m_configuration.getLocalPatientIdentifierTypeMap()
							.get(pid.getIdentifierType().getName());
					if (toAssigningAuthority.equals(domain))
						return pid;
				}
				return null;
			}
			return null; // no results
		}
		catch (Exception e) {
			e.printStackTrace();
			log.error("Error in PDQ Search", e);
			throw new MpiClientException(e);
		}
	}

	/**
	 * Imports patient from MPI? Was in not implemented due to lack of FHIR create capabilites in OpenMRS?
	 *
	 * @param patient
	 * @return
	 * @throws MpiClientException
	 */
	@Override
	public Patient importPatient(MpiPatient patient) throws MpiClientException {
		// Match against a local record (by our local-id identifier); update it, else create a new one.
		// Ported from HL7MpiClientServiceImpl.importPatient so import works in FHIR mode too.
		Patient patientRecord = Context.getService(MpiClientService.class).matchWithExistingPatient(patient);

		if (patientRecord != null) {
			log.info(String.format("Matched MPI patient with local patient %s; updating", patientRecord.getId()));
			for (PatientIdentifier id : patient.getIdentifiers()) {
				if (id.getIdentifierType() == null) {
					continue;
				}
				boolean hasId = false;
				for (PatientIdentifier eid : patientRecord.getIdentifiers()) {
					hasId |= eid.getIdentifier().equals(id.getIdentifier())
					        && eid.getIdentifierType().getId().equals(id.getIdentifierType().getId());
				}
				if (!hasId) {
					if (LocationBehavior.REQUIRED.equals(id.getIdentifierType().getLocationBehavior())
					        && id.getLocation() == null) {
						id.setLocation(Context.getLocationService().getDefaultLocation());
					}
					patientRecord.addIdentifier(id);
				}
			}
			patientRecord.getNames().clear();
			for (PersonName name : patient.getNames()) {
				patientRecord.addName(name);
			}
			patientRecord.getAddresses().clear();
			for (PersonAddress addr : patient.getAddresses()) {
				patientRecord.addAddress(addr);
			}
			patientRecord.setDead(patient.getDead());
			patientRecord.setDeathDate(patient.getDeathDate());
			patientRecord.setBirthdate(patient.getBirthdate());
			patientRecord.setBirthdateEstimated(patient.getBirthdateEstimated());
			patientRecord.setGender(patient.getGender());
		} else {
			boolean isPreferred = false;
			patientRecord = patient.toPatient();
			// Drop the enterprise/MPI id (the identifier with no local type) — it isn't a local identifier.
			PatientIdentifier ecidPid = null;
			for (PatientIdentifier id : patientRecord.getIdentifiers()) {
				if (id.getIdentifierType() == null) {
					ecidPid = id;
				}
				isPreferred |= id.getPreferred();
			}
			if (ecidPid != null) {
				patientRecord.removeIdentifier(ecidPid);
			}
			for (PatientIdentifier id : patientRecord.getIdentifiers()) {
				if (id.getIdentifierType() != null
				        && LocationBehavior.REQUIRED.equals(id.getIdentifierType().getLocationBehavior())
				        && id.getLocation() == null) {
					id.setLocation(Context.getLocationService().getDefaultLocation());
				}
			}
			if (!isPreferred && !patientRecord.getIdentifiers().isEmpty()) {
				patientRecord.getIdentifiers().iterator().next().setPreferred(true);
			}
		}

		Patient importedPatient;
		try {
			importedPatient = Context.getPatientService().savePatient(patientRecord);
		}
		catch (APIException e) {
			throw new MpiClientException("Unable to import patient from the MPI", e);
		}

		if (this.m_configuration.getUseOpenMRSRelationships() && patient.getRelationships() != null) {
			for (Relationship rel : patient.getRelationships()) {
				Context.getPersonService().saveRelationship(
				    new Relationship(importedPatient, rel.getPersonB(), rel.getRelationshipType()));
			}
		}
		return importedPatient;
	}

	/**
	 * Sends a patient to the MPI in FHIR format
	 */
	@Override
	public void exportPatient(MpiPatientExport patientExport) throws MpiClientException {
		org.hl7.fhir.r4.model.Patient admitMessage = null;

		try {
			admitMessage = patientTranslator.toFhirResource(patientExport.getPatient());
			admitMessage.getNameFirstRep().setUse(HumanName.NameUse.OFFICIAL);

			// Sanitize the exported identifier set so we never propagate corruption to the registry:
			//   - drop UUID-valued ids (the local ECID/golden id and any imported record uuid — these
			//     are internal MPI ids, not site identifiers),
			//   - drop blanks, and dedupe by system (keep the first value per system).
			// The site source-key is added fresh below, so it is unaffected. PUT-by-id then REPLACES
			// the OpenCR resource with this clean set (no accumulation across re-exports).
			List<Identifier> cleanedIds = new ArrayList<Identifier>();
			Set<String> seenIdSystems = new HashSet<String>();
			for (Identifier id : admitMessage.getIdentifier()) {
				String sys = id.getSystem();
				String val = id.getValue();
				if (val == null || val.isEmpty() || UUID_VALUE.matcher(val).matches()) {
					continue;
				}
				if (sys != null && !sys.isEmpty() && !seenIdSystems.add(sys)) {
					continue;
				}
				cleanedIds.add(id);
			}
			admitMessage.setIdentifier(cleanedIds);

			//           Set mother's name
			if (patientExport.getMothersMaidenName() != null) {
				Extension mothersMaidenName = new Extension();
				mothersMaidenName.setUrl("http://hl7.org/fhir/StructureDefinition/patient-mothersMaidenName");
				mothersMaidenName.setValue(new StringType(patientExport.getMothersMaidenName().getValue()));
				admitMessage.addExtension(mothersMaidenName);
			}
			//            Set Patient Phone number
			PersonAttribute patientTelephone = patientExport.getPatientTelephone();
			if (patientTelephone != null) {
				ContactPoint contactPoint = new ContactPoint();
				contactPoint.setId(patientTelephone.getUuid());
				contactPoint.setValue(patientTelephone.getValue());
				contactPoint.setSystem(ContactPoint.ContactPointSystem.PHONE);
				contactPoint.setUse(ContactPoint.ContactPointUse.MOBILE);
				admitMessage.addTelecom(contactPoint);
			}

			//            Patient Obs processing
			Set<Obs> patientObs = patientExport.getPatientObs();
			if (patientObs != null) {
				for (Obs obs : patientObs) {
					switch (obs.getConcept().getConceptId()) {
						case 165194: {//Place of birth address construct
							//                            165195=>locality, 165198=>country of residence, 1354=>village, 165197=>province, 165196=>communal section, 162725=> address
							Extension birthplace = new Extension();
							birthplace.setUrl("http://hl7.org/fhir/StructureDefinition/patient-birthPlace");
							Address address = parseAddress(obs);
							birthplace.setValue(address);
							admitMessage.addExtension(birthplace);
							break;
						}
						case 165210:
						case 165213:
						case 165212: {//Emergency contact construct, Primary medical disclosure construct,secondary medical disclosure construct
							//                            159635=> phone number, 164352=> relationship to patient, 163258 => name of contact,
							//                            165196 => communal section, 165195=> locality, 165198=> country of residence, 1354=> village, 165197=> province, 162725=> address
							admitMessage.addContact(translatePatientContact(obs));
							break;
						}
					}
				}
			}

			IGenericClient client = this.getClient(false);

			// Build the SEDISH source-key (mspp_code-patient_id) so the real-time feed and the
			// consolidated batch feed converge to a SINGLE source record in OpenCR (both feeds carry the
			// same key and upsert on it). Only when the site's mspp code is configured.
			String mspp = this.m_configuration.getMsppCode();
			String sourceKey = null;
			if (mspp != null && !mspp.isEmpty() && patientExport.getPatient().getPatientId() != null) {
				sourceKey = mspp + "-" + patientExport.getPatient().getPatientId();
				admitMessage.addIdentifier()
						.setSystem(this.m_configuration.getSourceKeySystem())
						.setValue(sourceKey);
			}

			// OpenCR does NOT support FHIR conditional-update (PUT /Patient?identifier=...): it
			// responds 404 "Cannot PUT /fhir/Patient". PUT by a stable id (the patient uuid) instead
			// -- OpenCR matches/dedupes on the in-resource source-key identifier, so this is
			// idempotent and converges with the consolidated batch feed, which also PUTs by id (see
			// the fhir-router mediator). The source-key identifier added above is what makes both
			// feeds resolve to a single source record.
			String stableId = patientExport.getPatient().getUuid();
			if (stableId != null && !stableId.isEmpty()) {
				admitMessage.setId(stableId);
			}
			MethodOutcome result = client.update().resource(admitMessage).execute();
			if (result.getId() == null && result.getResource() == null)
				throw new MpiClientException("Error from MPI :> patient upsert returned no id");

		}
		catch (FhirClientConnectionException e) {
			log.error("Error in FHIR PIX message", e);
		}
		catch (MpiClientException e) {
			log.error("Error in FHIR PIX message", e);
			e.printStackTrace();
			throw e;
		}
		catch (Exception e) {
			e.printStackTrace();
			log.error(e);
		}

	}

	private Address parseAddress(Obs obs) {
		Address fhirAddress = new Address();
		fhirAddress.setUse(Address.AddressUse.HOME);
		obs.getGroupMembers().forEach(member -> {
			switch (member.getConcept().getConceptId()) {
				case 165197: {
					//                    Province of residence
					fhirAddress.setState(member.getValueText());
					break;
				}
				case 165196: {
					//                    Communal section
					fhirAddress.addExtension("Communal section", new StringType(member.getValueText()));
					break;
				}
				case 165198: {
					//                    Country
					fhirAddress.setCountry(member.getValueText());
					break;
				}
				case 162725: {
					//                    Address (text)
					fhirAddress.addExtension("Address Text", new StringType(member.getValueText()));
					break;
				}
				case 1354: {
					//                    Village
					fhirAddress.setCity(member.getValueText());
					break;
				}
				case 165195: {
					//                    Locality
					fhirAddress.addExtension("Locality", new StringType(member.getValueText()));
					break;
				}
				default: {
					//                    Do nothing
				}
			}
		});

		return fhirAddress;
	}

	private org.hl7.fhir.r4.model.Patient.ContactComponent translatePatientContact(org.openmrs.Obs patientOb) {
		Set<org.openmrs.Obs> contactMembers = patientOb.getGroupMembers(false);
		//		Add patient contact -
		org.hl7.fhir.r4.model.Patient.ContactComponent contactComponent = new org.hl7.fhir.r4.model.Patient.ContactComponent();

		Address address = parseAddress(patientOb);
		contactComponent.setAddress(address);

		Reference reference = new Reference();
		reference.setDisplay(patientOb.getConcept().getName().getName());
		contactComponent.setOrganization(reference);

		for (org.openmrs.Obs cm : contactMembers) {
			if (cm.getConcept().getConceptId() == 163258) {
				//				Process contact name
				HumanName contactName = new HumanName();
				String[] names = cm.getValueText().split(" ");

				if (names.length > 1) {
					contactName.setFamily(names[1]);
					List<StringType> ns = new ArrayList<StringType>() {{
						add(new StringType(names[0]));
					}};
					contactName.setGiven(ns);
				} else if (names.length == 1) {
					contactName.setFamily(names[0]);
				}
				contactComponent.setName(contactName);
			} else if (cm.getConcept().getConceptId() == 159635) {
				//				Process contact's phone number
				ContactPoint telco = new ContactPoint();
				telco.setSystem(ContactPoint.ContactPointSystem.PHONE);
				telco.setValue(cm.getValueText());
				telco.setUse(ContactPoint.ContactPointUse.MOBILE);
				List<ContactPoint> contactPoints = new ArrayList<ContactPoint>() {{
					add(telco);
				}};
				contactComponent.setTelecom(contactPoints);
			} else if (cm.getConcept().getConceptId() == 164352) {
				//            	Process relationship to patient
				CodeableConcept concept = new CodeableConcept();
				concept.setText(cm.getValueCoded().getName().getName());
				contactComponent.addRelationship(concept);
			}
		}
		return contactComponent;
	}

	/**
	 * Updates the MPI patient information with local patient information
	 */
	@Override
	public void updatePatient(MpiPatientExport patientExport) throws MpiClientException {
		org.hl7.fhir.r4.model.Patient admitMessage = null;

		try {
			admitMessage = patientTranslator.toFhirResource(patientExport.getPatient());
			admitMessage.getNameFirstRep().setUse(HumanName.NameUse.OFFICIAL);
			// Temporary URI identifier
			admitMessage.addIdentifier().setSystem("urn:ietf:rfc:3986")
					.setValue(this.m_configuration.getLocalPatientIdRoot() + patientExport.getPatient().getUuid());
			IGenericClient client = this.getClient(false);
			MethodOutcome result = client.update().resource(admitMessage).execute();
			if (!result.getCreated())
				throw new MpiClientException(
						String.format("Error from MPI :> %s", result.getResource().getClass().getName()));
		}
		catch (MpiClientException e) {
			log.error("Error in FHIR PIX message", e);
			e.printStackTrace();
			throw e;
		}
		catch (Exception e) {
			e.printStackTrace();
			log.error(e);
			throw new MpiClientException(e);
		}
	}

	@Override
	public AuditLogger getAuditLogger() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
		this.applicationContext = applicationContext;
	}

	/**
	 * Resolve the OpenCR golden record id (CRUID) for a locally-registered patient: query the MPI by
	 * the patient's SEDISH source-key (mspp_code-patientId), find the linked golden record (by golden
	 * tag), and follow any replaced-by link to the surviving golden. Returns null when no golden is
	 * found.
	 *
	 * The source-key is used deliberately instead of the iSantePlus ID: iSantePlus IDs are issued
	 * per-facility and are NOT globally unique (the same value can belong to different people at
	 * different sites), so resolving by iSantePlus ID can return a DIFFERENT person's golden — the
	 * cause of ECID/Golden-ID cross-linking. The source-key is globally unique and is stamped on every
	 * exported record (see exportPatient), so it resolves to exactly this patient's golden.
	 */
	public String getGoldenRecordId(Patient patient) {
		try {
			String mspp = this.m_configuration.getMsppCode();
			if (mspp == null || mspp.isEmpty() || patient.getPatientId() == null) {
				log.warn("No mspp code or patient id; cannot resolve golden record id by source-key");
				return null;
			}
			String system = this.m_configuration.getSourceKeySystem();
			String value = mspp + "-" + patient.getPatientId();

			Bundle bundle = this.getClient(true).search()
					.byUrl("Patient?identifier=" + system + "|" + value + "&_include=Patient:link")
					.returnBundle(Bundle.class).execute();

			org.hl7.fhir.r4.model.Patient golden = null;
			for (BundleEntryComponent entry : bundle.getEntry()) {
				if (entry.getResource() instanceof org.hl7.fhir.r4.model.Patient) {
					org.hl7.fhir.r4.model.Patient p = (org.hl7.fhir.r4.model.Patient) entry.getResource();
					if (p.hasMeta() && p.getMeta().hasTag() && p.getMeta().getTagFirstRep().hasCode()
							&& p.getMeta().getTagFirstRep().getCode().equals(m_configuration.getGoldenRecordUuid())) {
						golden = p;
						break;
					}
				}
			}
			if (golden == null) {
				return null;
			}

			// If this golden was merged into another, follow the replaced-by link to the survivor.
			for (PatientLinkComponent link : golden.getLink()) {
				if (link.getType() != null && "replaced-by".equalsIgnoreCase(link.getType().toCode())
						&& link.getOther() != null && link.getOther().getReferenceElement().hasIdPart()) {
					return link.getOther().getReferenceElement().getIdPart();
				}
			}
			return golden.getIdElement().getIdPart();
		}
		catch (Exception e) {
			log.error("Error resolving golden record id", e);
			return null;
		}
	}

	/**
	 * Return every source record linked to the patient's golden record (the cross-facility occurrences),
	 * resolved directly from the golden's links. Uses the resolved golden id, then fetches the golden with
	 * {@code _include=Patient:link} so all linked source records come back in one bundle. The golden record
	 * itself (which carries no demographics in this topology) is excluded; each occurrence's site is taken
	 * from its identifier location extension when available.
	 */
	public List<MpiPatient> getGoldenRecordOccurrences(Patient patient) {
		// Records at OTHER facilities: resolve this patient's golden, then list its sources excluding
		// the local EMR's own copy (OpenCR holds it under the local patient uuid, set at export time).
		String goldenId = this.getGoldenRecordId(patient);
		return occurrencesForGolden(goldenId, patient != null ? patient.getUuid() : null);
	}

	/**
	 * List every source record under a golden record (CRUID) directly — for viewing a patient's
	 * registry record by golden id, independent of any local patient record.
	 */
	public List<MpiPatient> getGoldenRecordOccurrencesByGoldenId(String goldenId) {
		return occurrencesForGolden(goldenId, null);
	}

	private List<MpiPatient> occurrencesForGolden(String goldenId, String excludeSourceId) {
		List<MpiPatient> occurrences = new java.util.ArrayList<MpiPatient>();
		try {
			if (goldenId == null || goldenId.isEmpty()) {
				return occurrences;
			}

			Bundle bundle = this.getClient(true).search()
					.byUrl("Patient?_id=" + goldenId + "&_include=Patient:link")
					.returnBundle(Bundle.class).execute();

			for (BundleEntryComponent entry : bundle.getEntry()) {
				if (!(entry.getResource() instanceof org.hl7.fhir.r4.model.Patient)) {
					continue;
				}
				org.hl7.fhir.r4.model.Patient p = (org.hl7.fhir.r4.model.Patient) entry.getResource();

				// Skip the golden record itself (tagged with the golden uuid; carries no demographics).
				if (p.hasMeta() && p.getMeta().hasTag() && p.getMeta().getTagFirstRep().hasCode()
						&& p.getMeta().getTagFirstRep().getCode().equals(m_configuration.getGoldenRecordUuid())) {
					continue;
				}

				// Skip the local EMR's own copy when requested — occurrences list OTHER facilities only.
				if (excludeSourceId != null && excludeSourceId.equals(p.getIdElement().getIdPart())) {
					continue;
				}

				// Parse each occurrence independently so one unparseable record does not drop the whole
				// list. The fhir2 (1.11.x) address translator throws NPEs on some source address
				// structures (e.g. consolidated-server records); we do not display address here, so drop
				// it before translating to avoid that failure.
				try {
					p.setAddress(new java.util.ArrayList<org.hl7.fhir.r4.model.Address>());

					MpiPatient mpiPatient = fhirUtil.parseFhirPatient(p, patientTranslator.toOpenmrsType(p));

					// Derive a site label from the first identifier that carries a location extension.
					for (org.hl7.fhir.r4.model.Identifier fhirId : p.getIdentifier()) {
						org.hl7.fhir.r4.model.Extension locExt = fhirId
								.getExtensionByUrl("http://fhir.openmrs.org/ext/patient/identifier#location");
						if (locExt != null && locExt.getValue() instanceof org.hl7.fhir.r4.model.Reference) {
							String site = ((org.hl7.fhir.r4.model.Reference) locExt.getValue()).getDisplay();
							if (site != null && !site.isEmpty()) {
								mpiPatient.setSourceLocation(site);
								break;
							}
						}
					}

					occurrences.add(mpiPatient);
				}
				catch (Exception perRecord) {
					log.warn("Skipping an occurrence that could not be parsed", perRecord);
				}
			}
		}
		catch (Exception e) {
			log.error("Error resolving golden record occurrences", e);
		}
		return occurrences;
	}

	/**
	 * Resolve the golden record id and store it on the patient as the configured golden-record
	 * identifier type (default ECID). Suppresses the export advice so the write does not re-trigger a feed.
	 */
	public void synchronizeGoldenRecordId(Patient patient) {
		String goldenId = this.getGoldenRecordId(patient);
		if (goldenId == null || goldenId.isEmpty()) {
			return;
		}
		PatientIdentifierType type = Context.getPatientService()
				.getPatientIdentifierTypeByName(m_configuration.getGoldenRecordIdentifierType());
		if (type == null) {
			log.warn(String.format("Golden-record identifier type '%s' not found; cannot store golden id",
					m_configuration.getGoldenRecordIdentifierType()));
			return;
		}
		PatientIdentifier existing = patient.getPatientIdentifier(type);
		if (existing != null && goldenId.equals(existing.getIdentifier())) {
			return;
		}
		boolean prev = PatientSynchronizationAdvice.SUPPRESS.get();
		PatientSynchronizationAdvice.SUPPRESS.set(true);
		try {
			if (existing != null) {
				existing.setIdentifier(goldenId);
				Context.getPatientService().savePatientIdentifier(existing);
			} else {
				Location loc = Context.getLocationService().getDefaultLocation();
				PatientIdentifier pid = new PatientIdentifier(goldenId, type, loc);
				pid.setPatient(patient);
				Context.getPatientService().savePatientIdentifier(pid);
			}
		}
		finally {
			PatientSynchronizationAdvice.SUPPRESS.set(prev);
		}
	}
}
