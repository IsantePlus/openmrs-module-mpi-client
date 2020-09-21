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

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

import ca.uhn.hl7v2.HL7Exception;
import ca.uhn.hl7v2.model.DataTypeException;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.hl7.fhir.exceptions.FHIRException;
import org.hl7.fhir.r4.model.*;
import org.hl7.fhir.r4.model.Address.AddressUse;
import org.hl7.fhir.r4.model.Enumerations.AdministrativeGender;
import org.hl7.fhir.r4.model.HumanName.NameUse;
import org.hl7.fhir.r4.model.Patient.ContactComponent;
import org.openmrs.Patient;
import org.openmrs.Obs;
import org.openmrs.PatientIdentifier;
import org.openmrs.PatientIdentifierType;
import org.openmrs.PersonAddress;
import org.openmrs.PersonName;
import org.openmrs.api.ConceptService;
import org.openmrs.api.context.Context;
import org.openmrs.module.fhir2.FhirConstants;
import org.openmrs.module.fhir2.api.translators.TelecomTranslator;
import org.openmrs.module.fhir2.api.translators.impl.TelecomTranslatorImpl;
import org.openmrs.module.santedb.mpiclient.configuration.MpiClientConfiguration;
import org.openmrs.module.santedb.mpiclient.model.MpiPatient;

import static org.apache.commons.lang3.Validate.notNull;


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
	 * @return
	 */
	private PersonAddress interpretFhirAddress(Address addr) {

		PersonAddress pa = new PersonAddress();
		if (addr == null) {
			return pa;
		}
		// Set the address
		pa.setUuid(addr.getId());
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
	 * @return The interpreted name
	 */
	private PersonName interpretFhirName(HumanName name) {
		if (name == null) {
			return null;
		}
		PersonName pn = new PersonName();
		pn.setUuid(name.getId());

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

		notNull(patient, "The existing Openmrs Patient object should not be null");
		notNull(fhirPatient, "The Patient object should not be null");

//		Set UUID
//		patient.setUuid(fhirPatient.getId());



		// Attempt to load a patient by identifier
		Iterator identifierIterator = fhirPatient.getIdentifier().iterator();
		while(identifierIterator.hasNext()) {
			Identifier identifier = (Identifier)identifierIterator.next();
//			Ignore Code ST IDs
			if(identifier.hasType() && ("Code ST".equals(identifier.getType().getText()))){
				continue;
			}
			PatientIdentifier patientIdentifier = IdentifierTranslator.translateIdentifier(identifier);
			if(patientIdentifier != null){
				patient.addIdentifier(patientIdentifier);
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
			try {
				fhirPatient.getDeceasedBooleanType();
				patient.setDead(fhirPatient.getDeceasedBooleanType().booleanValue());
			}
			catch (FHIRException ignored) {}
			try {
				fhirPatient.getDeceasedDateTimeType();
				patient.setDead(true);
				patient.setDeathDate(fhirPatient.getDeceasedDateTimeType().getValue());
			}
			catch (FHIRException ignored) {}
		}
		
		// Addresses
		for (Address addr : fhirPatient.getAddress()) {
			// Skip bad addresses
			if (AddressUse.OLD.equals(addr.getUse()))
				continue;

			PersonAddress pa = this.interpretFhirAddress(addr);
			patient.addAddress(pa);
		}

//		Patient Telephone
		fhirPatient.getTelecom().stream().map(contactPoint -> translateTelecom(contactPoint)).distinct().filter(Objects::nonNull).forEach(patient::addAttribute);

//		Birth Location
		Extension patientBirthPlace = fhirPatient.getExtensionByUrl("http://hl7.org/fhir/StructureDefinition/patient-birthPlace");
		if(patientBirthPlace != null){
			Address birthPlaceAddress = (Address) patientBirthPlace.getValue();
			Obs obs = null;
			try {
				obs = translateAddressComponent(birthPlaceAddress);
				if (obs != null) {
					patient.addPatientObservation(obs);
				}
			} catch (ParseException e) {
				e.printStackTrace();
			}
		}


//		Patient Contacts
		for (ContactComponent contactComponent : fhirPatient.getContact()) {
			Obs obs = null;
			try {
				obs = translateContactComponent(contactComponent);
				if (obs != null) {
					patient.addPatientObservation(obs);
				}
			} catch (ParseException e) {
				e.printStackTrace();
			}
		}



//		Source Location
		Identifier identifierFirstRep = fhirPatient.getIdentifierFirstRep();
		if(identifierFirstRep.hasExtension("http://fhir.openmrs.org/ext/patient/identifier#location")){
			Extension locationExtension = identifierFirstRep.getExtensionByUrl("http://fhir.openmrs.org/ext/patient/identifier#location");
			Reference value = (Reference) locationExtension.getValue();
			patient.setSourceLocation(value.getDisplay());
		}

//		Mother's maiden name
		Extension mothersMaidenName = fhirPatient.getExtensionByUrl("http://hl7.org/fhir/StructureDefinition/patient-mothersMaidenName");
		if(mothersMaidenName != null){
			org.openmrs.PersonAttributeType attributeType = Context.getPersonService().getPersonAttributeTypeByName(m_configuration.getMothersAttributeName());
			org.openmrs.PersonAttribute attribute = new org.openmrs.PersonAttribute(attributeType,((StringType)mothersMaidenName.getValue()).getValue());
			patient.addAttribute(attribute);
		}





		return patient;
	}

	private Obs translateAddressComponent(Address birthPlaceAddress) throws ParseException {

		ConceptService conceptService = Context.getConceptService();
		org.openmrs.Location defaultLocation = Context.getLocationService().getDefaultLocation();
		org.openmrs.Obs parent = new  org.openmrs.Obs();
		Set<org.openmrs.Obs> contactMembers = new HashSet<>();

//		Set type
		parent.setConcept(conceptService.getConceptByUuid("165194AAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"));

//		Set location
		parent.setLocation(defaultLocation);

//		Process City
		org.openmrs.Obs obs;
		String city = birthPlaceAddress.getCity();
		obs = new org.openmrs.Obs();
		obs.setConcept(conceptService.getConcept(1354));
		obs.setValueAsString(String.valueOf(city));
		obs.setLocation(defaultLocation);
		obs.setObsDatetime(new Date());
		contactMembers.add(obs);



		String state = birthPlaceAddress.getState();
		obs = new org.openmrs.Obs();
		obs.setConcept(conceptService.getConcept(165197));
		obs.setValueAsString(String.valueOf(state));
		obs.setLocation(defaultLocation);
		obs.setObsDatetime(new Date());
		contactMembers.add(obs);


		String country = birthPlaceAddress.getCountry();
//			165198
		obs = new org.openmrs.Obs();
		obs.setConcept(conceptService.getConcept(165198));
		obs.setValueAsString(String.valueOf(country));
		obs.setLocation(defaultLocation);
		obs.setObsDatetime(new Date());
		contactMembers.add(obs);

		Iterator<Extension> iterator = birthPlaceAddress.getExtension().iterator();

		while (iterator.hasNext()){
			Extension nextExtension = iterator.next();
			processAddressExtension(conceptService, contactMembers, nextExtension);
		}

		parent.setGroupMembers(contactMembers);


		return parent;
	}

	public ContactComponent translatePatientContact(Obs patientOb) {
//		Process patient contact -
        ContactComponent contactComponent = new ContactComponent();
        Set<Obs> contactMembers = patientOb.getGroupMembers(false);
        for (Obs cm : contactMembers) {
//			TODO move to global peroperties and use UUID instead
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
                List<ContactPoint> contactPoints = new ArrayList<ContactPoint>() {{
                    add(telco);
                }};
                contactPoints.add(telco);
                contactComponent.setTelecom(contactPoints);
            } else if (cm.getConcept().getConceptId() == 164352) {
//            	Process relationship to patient
                CodeableConcept concept = new CodeableConcept();
                concept.setText(cm.getValueCodedName().getName());
                contactComponent.addRelationship(concept);

            } else if (cm.getConcept().getConceptId() == 164958) {
//            	Wrong mapping for address

            }
        }
        return contactComponent;
    }

    public org.openmrs.PersonAttribute translateTelecom(ContactPoint contactPoint){
		org.openmrs.PersonAttribute personAttribute = new org.openmrs.PersonAttribute();
		if (contactPoint == null) {
			return personAttribute;
		}
		personAttribute.setUuid(contactPoint.getId());
		personAttribute.setValue(contactPoint.getValue());
		personAttribute.setAttributeType(Context.getPersonService().getPersonAttributeTypeByUuid(
				Context.getAdministrationService().getGlobalProperty(FhirConstants.PERSON_CONTACT_POINT_ATTRIBUTE_TYPE)));
		return  personAttribute;
	}


	private org.openmrs.Obs translateContactComponent(ContactComponent contactComponent) throws ParseException {
		ConceptService conceptService = Context.getConceptService();
		org.openmrs.Location defaultLocation = Context.getLocationService().getDefaultLocation();
		org.openmrs.Obs parent = new  org.openmrs.Obs();
		Set<org.openmrs.Obs> contactMembers = new HashSet<>();

//		Set type
		Reference organization = contactComponent.getOrganization();
		if(organization != null){
			parent.setConcept(conceptService.getConceptByName(organization.getDisplay()));
		}
//		Set location
		parent.setLocation(defaultLocation);

//		set name
		if(contactComponent.hasName()){
			HumanName humanName = contactComponent.getName();
			if(humanName.hasFamily()){
				String contactName = "";
				if(humanName.hasGiven()){
					contactName += humanName.getGiven().toString() + " ";
				}
				contactName += humanName.getFamily();
				org.openmrs.Obs nameObs = new org.openmrs.Obs();
				nameObs.setConcept(conceptService.getConcept(163258));
				nameObs.setValueAsString(contactName);
				nameObs.setLocation(defaultLocation);
				nameObs.setObsDatetime(new Date());
				contactMembers.add(nameObs);
			}
		}


//		Process phone number
		if(contactComponent.hasTelecom()){
			ContactPoint telecomComponent = contactComponent.getTelecomFirstRep();
			org.openmrs.Obs telecomObs = new org.openmrs.Obs();
			telecomObs.setConcept(conceptService.getConcept(159635));
			telecomObs.setValueAsString(telecomComponent.getValue());
			telecomObs.setLocation(defaultLocation);
			telecomObs.setObsDatetime(new Date());
			contactMembers.add(telecomObs);
		}

//		Process relationship to patient
		if(contactComponent.hasRelationship()){
			CodeableConcept patientRelationship = contactComponent.getRelationshipFirstRep();
			org.openmrs.Obs relationshipObs = new org.openmrs.Obs();
			relationshipObs.setConcept(conceptService.getConcept(164352));
			relationshipObs.setValueCoded(conceptService.getConceptByName(patientRelationship.getText()));
			relationshipObs.setLocation(defaultLocation);
			relationshipObs.setObsDatetime(new Date());
			contactMembers.add(relationshipObs);
		}

//		Process contact address
		if(contactComponent.hasAddress()){
			org.openmrs.Obs obs;
			Address contactAddress = contactComponent.getAddress();
			String city = contactAddress.getCity();
			obs = new org.openmrs.Obs();
			obs.setConcept(conceptService.getConcept(1354));
			obs.setValueAsString(String.valueOf(city));
			obs.setLocation(defaultLocation);
			obs.setObsDatetime(new Date());
			contactMembers.add(obs);



			String state = contactAddress.getState();
			obs = new org.openmrs.Obs();
			obs.setConcept(conceptService.getConcept(165197));
			obs.setValueAsString(String.valueOf(state));
			obs.setLocation(defaultLocation);
			obs.setObsDatetime(new Date());
			contactMembers.add(obs);


			String country = contactAddress.getCountry();
//			165198
			obs = new org.openmrs.Obs();
			obs.setConcept(conceptService.getConcept(165198));
			obs.setValueAsString(String.valueOf(country));
			obs.setLocation(defaultLocation);
			obs.setObsDatetime(new Date());
			contactMembers.add(obs);

			Iterator<Extension> iterator = contactAddress.getExtension().iterator();

			while (iterator.hasNext()){
				Extension nextExtension = iterator.next();
				processAddressExtension(conceptService, contactMembers, nextExtension);
			}

		}

		parent.setGroupMembers(contactMembers);


		return parent;

	}

	private void processAddressExtension(ConceptService conceptService, Set<Obs> contactMembers, Extension nextExtension) throws ParseException {
		Obs obs = new Obs();
		org.openmrs.Concept obsConcept = null;
		switch (nextExtension.getUrl()){
			case "Communal section": {
				obsConcept = conceptService.getConcept(165196);
				break;
			}
			case "Locality": {
				obsConcept = conceptService.getConcept(165195);
				break;
			}
			case "Address Text": {
				obsConcept = conceptService.getConcept(162725);
				break;
			}
		}
		if(obsConcept!=null){
			obs.setConcept(obsConcept);
			obs.setValueAsString(String.valueOf(nextExtension.getValue()));
			obs.setLocation(Context.getLocationService().getDefaultLocation());
			obs.setObsDatetime(new Date());
			contactMembers.add(obs);
		}else{
//			Not processed given concept is missing

		}
	}

}
