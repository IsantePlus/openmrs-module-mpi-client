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
package org.openmrs.module.santedb.mpiclient.util;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import ca.uhn.hl7v2.HL7Exception;
import ca.uhn.hl7v2.model.DataTypeException;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.hl7.fhir.r4.model.Address;
import org.hl7.fhir.r4.model.Address.AddressUse;
import org.hl7.fhir.r4.model.BooleanType;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.DateType;
import org.hl7.fhir.r4.model.Enumerations.AdministrativeGender;
import org.hl7.fhir.r4.model.HumanName;
import org.hl7.fhir.r4.model.HumanName.NameUse;
import org.hl7.fhir.r4.model.Identifier;
import org.openmrs.Patient;
import org.openmrs.PatientIdentifier;
import org.openmrs.PatientIdentifierType;
import org.openmrs.PersonAddress;
import org.openmrs.PersonName;
import org.openmrs.api.context.Context;
import org.openmrs.module.santedb.mpiclient.configuration.MpiClientConfiguration;
import org.openmrs.module.santedb.mpiclient.model.MpiPatient;


public class FhirUtil {
	
	private final Log log = LogFactory.getLog(this.getClass());

	// locking object
	private final static Object s_lockObject = new Object();

	// Instance
	private static FhirUtil s_instance = null;

	// Get the HIE config
	private MpiClientConfiguration m_configuration = MpiClientConfiguration.getInstance();

	/**
	 * Creates a new message utility
	 */
	private FhirUtil() {
	}

	/**
	 * Get an instance of the message utility
	 */
	public static FhirUtil getInstance() {
		if (s_instance == null)
			synchronized (s_lockObject) {
				if (s_instance == null)
					s_instance = new FhirUtil();
			}
		return s_instance;
	}

	/**
	 * Update the specified FHIR identifier
	 * 
	 * @param fhirIdentifier    The FHIR identifier to be updated
	 * @param patientIdentifier The patient identifier to be mapped
	 * @param domain            The domain in which the identifier belongs
	 */
	private void updateFhirId(Identifier fhirIdentifier, String patientIdentifier, String domain) {
		// URL
		if (domain.matches(
				"^([a-z0-9+.-]+):(?://(?:((?:[a-z0-9-._~!$&'()*+,;=:]|%[0-9A-F]{2})*)@)?((?:[a-z0-9-._~!$&'()*+,;=]|%[0-9A-F]{2})*)(?::(\\d*))?(/(?:[a-z0-9-._~!$&'()*+,;=:@/]|%[0-9A-F]{2})*)?|(/?(?:[a-z0-9-._~!$&'()*+,;=:@]|%[0-9A-F]{2})+(?:[a-z0-9-._~!$&'()*+,;=:@/]|%[0-9A-F]{2})*)?)(?:\\?((?:[a-z0-9-._~!$&'()*+,;=:/?@]|%[0-9A-F]{2})*))?(?:#((?:[a-z0-9-._~!$&'()*+,;=:/?@]|%[0-9A-F]{2})*))?$"))
			fhirIdentifier.setSystem(domain);
		else if (domain.matches("^(\\d+?\\.){1,}\\d+$")) // domain is an oid
			fhirIdentifier.setSystem(String.format("urn:oid:%s", domain));
		else
			fhirIdentifier.setSystem(domain);
		fhirIdentifier.setValue(patientIdentifier);
		fhirIdentifier.setType(new CodeableConcept(
				new Coding("http://terminology.hl7.org/CodeSystem/v2-0203", "PT", "Patient External Identifier")));
	}

	/**
	 * Interpret the XAD as a person address
	 * 
	 * @param xad
	 * @return
	 */
	private PersonAddress interpretFhirAddress(Address addr) {
		PersonAddress pa = new PersonAddress();

		// Set the address
		if(addr.hasLine())
			pa.setAddress1(addr.getLine().get(0).asStringValue());
		if(addr.hasCity())
			pa.setCityVillage(addr.getCity());
		if(addr.hasCountry())
			pa.setCountry(addr.getCountry());
		if(addr.hasDistrict())
			pa.setCountyDistrict(addr.getDistrict());
		if(addr.hasPostalCode())
			pa.setPostalCode(addr.getPostalCode());
		if(addr.hasState())
			pa.setStateProvince(addr.getState());

		if (AddressUse.HOME.equals(addr.getUse()))
			pa.setPreferred(true);

		return pa;
	}
	
	/**
	 * Interpret the CX ID as a patient Identifier
	 * 
	 * @param id
	 */
	private PatientIdentifier interpretFhirId(Identifier id) {

		PatientIdentifierType pit = null;
		HashMap<String, String> authorityMaps = this.m_configuration.getLocalPatientIdentifierTypeMap();

		// Get the domain we're lookng for
		String domain = id.getSystem();
		if (domain == null)
			return null;

		for (String key : authorityMaps.keySet()) {
			if (domain.equals(authorityMaps.get(key))) {
				pit = Context.getPatientService().getPatientIdentifierTypeByName(key);
				if (pit == null)
					this.log.warn(String.format("%s is mapped to %s but cannot find %s", domain, key, key));
			}
		}

		// No map, so we should try lookup by the original system provided 
		if (pit == null) {
			pit = Context.getPatientService().getPatientIdentifierTypeByName(domain);
		}

		if (pit == null) {
			this.log.warn(String.format("ID domain %s has no known mapping to a Patient ID type",
					domain));
			return null;
		}

		PatientIdentifier patId = new PatientIdentifier(id.getValue(), pit, null);

		// Do not include the local identifier
		if (domain.equals(this.m_configuration.getNationalPatientIdRoot()))
			patId.setPreferred(true);

		return patId;
	}
	
	/**
	 * Updates the provided fhir name with the person name
	 * 
	 * @param name The name to be updated with the contents of pn
	 * @param pn   The person name to convert
	 * @throws RegexSyntaxException
	 */
	private void updateFhirName(HumanName name, PersonName pn)  {

		String nameRewrite = this.m_configuration.getNameRewriteRule();

		if (pn.getFamilyName() != null && !pn.getFamilyName().equals("(NULL)"))
			name.setFamily(pn.getFamilyName());
		if (pn.getFamilyName2() != null)
			name.setFamily(name.getFamily() + " " + pn.getFamilyName2());
		if (pn.getGivenName() != null && !pn.getGivenName().equals("(NULL)"))
			name.addGiven(pn.getGivenName());
		if (pn.getMiddleName() != null)
			name.addGiven(pn.getMiddleName());
		if (pn.getPrefix() != null)
			name.addPrefix(pn.getPrefix());

		if (pn.getPreferred())
			name.setUse(NameUse.OFFICIAL);
		else
			name.setUse(NameUse.USUAL);

	}

	/**
	 * Interpret the FHIR HumanName as a Patient Name
	 * 
	 * @param pn The name to interpret
	 * @return The interpreted name
	 */
	private PersonName interpretFhirName(HumanName name) {
		PersonName pn = new PersonName();

		if (name.getFamily() == null
				|| name.getFamily().isEmpty())
			pn.setFamilyName("(NULL)");
		else
			pn.setFamilyName(name.getFamily());

		// Given name
		if(!name.hasGiven())
			pn.setGivenName("(NULL)");
		else
			pn.setGivenName(name.getGivenAsSingleString());

		pn.setPrefix(name.getPrefixAsSingleString());

		if (NameUse.OFFICIAL.equals(name.getUse()))
			pn.setPreferred(true);

		return pn;
	}

	/**
	 * Updates the specified HL7v2 address
	 * 
	 * @param xad
	 * @param pa
	 * @throws DataTypeException
	 */
	private void updateFhirAddress(Address address, PersonAddress pa) throws DataTypeException {
		if (pa.getAddress1() != null)
			address.addLine(pa.getAddress1());
		if (pa.getAddress2() != null)
			address.addLine(pa.getAddress2());
		if (pa.getCityVillage() != null)
			address.setCity(pa.getCityVillage());
		if (pa.getCountry() != null)
			address.setCountry(pa.getCountry());
		else if (this.m_configuration.getDefaultCountry() != null
				&& !this.m_configuration.getDefaultCountry().isEmpty())
			address.setCountry(this.m_configuration.getDefaultCountry());
		if (pa.getCountyDistrict() != null)
			address.setDistrict(pa.getCountyDistrict());
		if (pa.getPostalCode() != null)
			address.setPostalCode(pa.getPostalCode());
		if (pa.getStateProvince() != null)
			address.setState(pa.getStateProvince());

		if (pa.getPreferred())
			address.setUse(AddressUse.HOME);
	}

	/**
	 * @summary Constructs a FHIR patient model from the specified OpenMRS patient
	 * @param patient     The patient to be constructed
	 * @param localIdOnly When true, only use the local ID
	 * @return The FHIR Patient
	 * @throws HL7Exception
	 * @throws RegexSyntaxException
	 */
	// TODO: replace with fhir2 functionality
	public org.hl7.fhir.r4.model.Patient createFhirPatient(Patient patient, boolean localIdOnly)
			throws HL7Exception {

		// Update the PID information
		HashMap<String, String> exportIdentifiers = this.m_configuration.getLocalPatientIdentifierTypeMap();

		// Patient
		org.hl7.fhir.r4.model.Patient retVal = new org.hl7.fhir.r4.model.Patient();

		// Configuration states not to append local patient identity domain
		if ("-".equals(this.m_configuration.getLocalPatientIdRoot())) {

			// Preferred domain
			String domain = this.m_configuration.getPreferredCorrelationDomain();
			if (domain == null || domain.isEmpty())
				throw new HL7Exception(
						"Cannot determine update correlation id, please set a preferred correlation identity domain");
			else {
				for (PatientIdentifier patIdentifier : patient.getIdentifiers()) {
					String thisDomain = exportIdentifiers.get(patIdentifier.getIdentifierType().getName());
					if (domain.equals(thisDomain)) {
						this.updateFhirId(retVal.addIdentifier(), patIdentifier.getIdentifier(), domain);
					}
				}
			}
		} else {
			Identifier idnumber = retVal.addIdentifier();
			this.updateFhirId(idnumber, patient.getId().toString(), this.m_configuration.getLocalPatientIdRoot());
			idnumber.setType(new CodeableConcept(
					new Coding("http://terminology.hl7.org/CodeSystem/v2-0203", "PI", "Patient External Identifier")));

			// Other identifiers
			if (!localIdOnly) {

				// Export IDs
				for (PatientIdentifier patIdentifier : patient.getIdentifiers()) {
					String domain = exportIdentifiers.get(patIdentifier.getIdentifierType().getName());
					if (domain != null) {
						this.updateFhirId(retVal.addIdentifier(), patIdentifier.getIdentifier(), domain);
					} else
						log.warn(String.format("Ignoring domain %s as it is not configured",
								patIdentifier.getIdentifierType().getName()));

				}
			}
		}

		// Names
		for (PersonName pn : patient.getNames())
			if (!pn.getFamilyName().equals("(none)") && !pn.getGivenName().equals("(none)"))
				this.updateFhirName(retVal.addName(), pn);

		// Gender
		if ("F".equals(patient.getGender()))
			retVal.setGender(AdministrativeGender.FEMALE);
		else if ("M".equals(patient.getGender()))
			retVal.setGender(AdministrativeGender.MALE);

		// Date of birth
		if (patient.getBirthdateEstimated())
			retVal.setBirthDateElement(new DateType(new SimpleDateFormat("yyyy").format(patient.getBirthdate())));
		else
			retVal.setBirthDate(patient.getBirthdate());

		// Addresses
		for (PersonAddress pa : patient.getAddresses()) {
			this.updateFhirAddress(retVal.addAddress(), pa);
		}

		// Death?
		if (patient.getDead()) {

			if (patient.getDeathDate() == null)
				retVal.setDeceased(new BooleanType(true));
			else
				retVal.setDeceased(new DateType(patient.getDeathDate()));
		}

		return retVal;
	}

	/**
	 * @summary Parse a FHIR patient into an OpenMRS patient
	 * @param fhirPatient The FHIR patient to be parsed
	 * @return The OpenMRS patient
	 */
	// TODO: replace with fhir2 functionality (translator MpiPatient <-> Patient)
	public MpiPatient parseFhirPatient(org.hl7.fhir.r4.model.Patient fhirPatient)
	{
		MpiPatient patient = new MpiPatient();
		// Attempt to load a patient by identifier
		for (Identifier id : fhirPatient.getIdentifier()) {
			// ID is a local identifier
			if (this.m_configuration.getLocalPatientIdRoot().equals(id.getSystem())) {
				if (StringUtils.isNumeric(id.getValue()))
					patient.setId(Integer.parseInt(id.getValue()));
				else {
					this.log.warn(String.format(
							"Patient identifier %s in %s claims to be from local domain but is not in a valid format",
							id.getValue(),
							id.getSystem()));
					continue;
				}
			}
			// ID is the preferred correlation domain
			else if (this.m_configuration.getPreferredCorrelationDomain() != null
					&& !this.m_configuration.getPreferredCorrelationDomain().isEmpty()
					&& (this.m_configuration.getPreferredCorrelationDomain().equals(id.getSystem()))) {

				PatientIdentifier patientIdentifier = this.interpretFhirId(id);
				List<PatientIdentifierType> identifierTypes = new ArrayList<PatientIdentifierType>();
				identifierTypes.add(patientIdentifier.getIdentifierType());
				List<PatientIdentifier> matchPatientIds = Context.getPatientService().getPatientIdentifiers(patientIdentifier.getIdentifier(), identifierTypes, null, null, null);

				if (matchPatientIds != null && matchPatientIds.size() > 0)
					patient.setId(matchPatientIds.get(0).getPatient().getId());
				patient.addIdentifier(patientIdentifier);
			} else {
				PatientIdentifier patId = this.interpretFhirId(id);
				if (patId != null)
					patient.addIdentifier(patId);
			}
		}

		// Enterprise root? 
		if(null != this.m_configuration.getEnterprisePatientIdRoot() && !this.m_configuration.getEnterprisePatientIdRoot().isEmpty())
		{
			Identifier fhirSysId = new Identifier();
			fhirSysId.setSystem(this.m_configuration.getEnterprisePatientIdRoot());
			fhirSysId.setValue(fhirPatient.getIdElement().toUnqualifiedVersionless().getValue());
			log.warn(String.format("Enterprise ID %s will be mapped", fhirPatient.getIdElement().toUnqualifiedVersionless().getValue()));
			PatientIdentifier sysId = this.interpretFhirId(fhirSysId);
			if(sysId != null) {
				patient.addIdentifier(sysId);
			}
		}
		
		// Attempt to copy names
		for (HumanName name : fhirPatient.getName()) {

			PersonName pn = this.interpretFhirName(name);
			patient.addName(pn);
		}

		// Copy gender
		if(AdministrativeGender.FEMALE.equals(fhirPatient.getGender()))
			patient.setGender("F");
		else if(AdministrativeGender.MALE.equals(fhirPatient.getGender()))
			patient.setGender("M");
		else 
			patient.setGender("U");

		// Copy DOB
		if (fhirPatient.hasBirthDate()) {
			patient.setBirthdate(fhirPatient.getBirthDate());
			if(fhirPatient.getBirthDateElement().getValueAsString().length() < 10) // Approx
				patient.setBirthdateEstimated(true);
		}

		// Death details
		if (fhirPatient.hasDeceased()) {
			if(fhirPatient.getDeceased() instanceof DateType)
				patient.setDeathDate(fhirPatient.getDeceasedDateTimeType().getValue());
			else 
				patient.setDead(fhirPatient.getDeceasedBooleanType().getValue());
		}
		
		// Addresses
		for (Address addr : fhirPatient.getAddress()) {
			// Skip bad addresses
			if (AddressUse.OLD.equals(addr.getUse()))
				continue;

			PersonAddress pa = this.interpretFhirAddress(addr);
			patient.addAddress(pa);
		}

		return patient;
	}
}
