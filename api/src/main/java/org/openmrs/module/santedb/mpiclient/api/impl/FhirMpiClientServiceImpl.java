package org.openmrs.module.santedb.mpiclient.api.impl;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.Base64;
import java.util.Date;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.dcm4che3.net.audit.AuditLogger;
import org.openmrs.Patient;
import org.openmrs.PatientIdentifier;
import org.openmrs.module.santedb.mpiclient.api.MpiClientWorker;
import org.openmrs.module.santedb.mpiclient.configuration.MpiClientConfiguration;
import org.openmrs.module.santedb.mpiclient.exception.MpiClientException;
import org.openmrs.module.santedb.mpiclient.model.MpiPatient;
import org.openmrs.module.santedb.mpiclient.util.MessageUtil;

import com.google.common.io.CharStreams;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.rest.api.EncodingEnum;
import ca.uhn.fhir.rest.api.MethodOutcome;
import ca.uhn.fhir.rest.client.api.IGenericClient;
import ca.uhn.fhir.rest.client.interceptor.BearerTokenAuthInterceptor;

/**
 * MPI Client Service Implementation using FHIR
 * 
 * @author fyfej
 *
 */
public class FhirMpiClientServiceImpl implements MpiClientWorker {

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

	/**
	 * Get the client as configured in this copy of the OMOD
	 */
	private IGenericClient getClient() throws MpiClientException {
		
		FhirContext ctx = FhirContext.forR4();
		IGenericClient client = ctx.newRestfulGenericClient(this.m_configuration.getPixEndpoint());
		client.setEncoding(EncodingEnum.JSON);
		
		// Is an IDP provided?
		if(this.m_configuration.getIdentityProviderUrl() != null &&
				!this.m_configuration.getIdentityProviderUrl().isEmpty())
		{
			
			// Call the IDP
			CloseableHttpClient oauthClientCredentialsClient = HttpClientBuilder.create().build();
			try {
				HttpPost post = new HttpPost(this.m_configuration.getIdentityProviderUrl());
				post.addHeader("Content-Type", "application/x-www-form-urlencoded");
				
				// HACK: SanteMPI requires either X.509 node authentication (configured via JKS) but can also use the X-Device-Authorization header
				// Since the JKS / X.509 node authentication is not supported, we'll have to use the X-DeviceAuthorization
				String clientSecret = this.m_configuration.getMsh8Security(), deviceSecret = null;
				if(clientSecret.contains("+"))
				{
					String[] clientParts = clientSecret.split("\\+");
					clientSecret = clientParts[1];
					deviceSecret = clientParts[0];
					
					// Now append the proper header for device authentication
					post.addHeader("X-Device-Authorization", String.format("basic %s", Base64.getEncoder().encodeToString(String.format("%s|%s:%s", this.m_configuration.getLocalApplication(), this.m_configuration.getLocalFacility(), deviceSecret).getBytes())));
				}
				
				post.setEntity(new StringEntity(String.format("client_id=%s&client_secret=%s&grant_type=client_credentials&socpe=*", this.m_configuration.getLocalApplication(), clientSecret)));
				
				HttpResponse response = oauthClientCredentialsClient.execute(post);
				if(response.getStatusLine().getStatusCode() == HttpStatus.SC_OK) 
				{
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
				}
				else 
					throw new Exception(String.format("Identity provider responded with %s", response.getStatusLine().getStatusCode()));
				
			} catch (Exception e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
				throw new MpiClientException(String.format("Could not authenticate client %s", this.m_configuration.getLocalApplication()), e);
			}
			finally {
				try {
					oauthClientCredentialsClient.close();
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
		}
		return client;
	}
	
	/**
	 * Search for patients
	 */
	@Override
	public List<MpiPatient> searchPatient(String familyName, String givenName, Date dateOfBirth, boolean fuzzyDate,
			String gender, String stateOrRegion, String cityOrTownship, PatientIdentifier patientIdentifier,
			PatientIdentifier mothersIdentifier, String nextOfKinName, String birthPlace) throws MpiClientException {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public MpiPatient getPatient(String identifier, String assigningAuthority) throws MpiClientException {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public PatientIdentifier resolvePatientIdentifier(Patient patient, String toAssigningAuthority)
			throws MpiClientException {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void synchronizePatientEnterpriseId(Patient patient) throws MpiClientException {
		// TODO Auto-generated method stub

	}

	@Override
	public Patient importPatient(MpiPatient patient) throws MpiClientException {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Patient matchWithExistingPatient(Patient remotePatient) {
		// TODO Auto-generated method stub
		return null;
	}

	/**
	 * Sends a patient to the MPI in FHIR format
	 */
	@Override
	public void exportPatient(Patient patient) throws MpiClientException {
		// TODO Auto-generated method stub

		org.hl7.fhir.r4.model.Patient admitMessage = null;

		try {
			admitMessage = this.m_messageUtil.CreateFhirPatient(patient, false);
			IGenericClient client = this.getClient();
			MethodOutcome result = client.create().resource(admitMessage).execute();
			if(!result.getCreated())
				throw new MpiClientException(String.format("Error from MPI :> %s", result.getResource().getClass().getName()));
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
	public void updatePatient(Patient patient) throws MpiClientException {
		// TODO Auto-generated method stub

	}

	@Override
	public AuditLogger getAuditLogger() {
		// TODO Auto-generated method stub
		return null;
	}

}
