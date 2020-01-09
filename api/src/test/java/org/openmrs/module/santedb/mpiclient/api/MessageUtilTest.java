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
package org.openmrs.module.santedb.mpiclient.api;

import static org.junit.Assert.*;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.List;


import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.openmrs.GlobalProperty;
import org.openmrs.Patient;
import org.openmrs.PatientIdentifier;
import org.openmrs.PatientIdentifierType;
import org.openmrs.Person;
import org.openmrs.PersonAttribute;
import org.openmrs.PersonAttributeType;
import org.openmrs.PersonName;
import org.openmrs.Relationship;
import org.openmrs.RelationshipType;
import org.openmrs.api.context.Context;
import org.openmrs.module.santedb.mpiclient.configuration.MpiClientConfiguration;
import org.openmrs.module.santedb.mpiclient.exception.MpiClientException;
import org.openmrs.module.santedb.mpiclient.model.MpiPatient;
import org.openmrs.module.santedb.mpiclient.util.MessageUtil;
import org.openmrs.test.BaseModuleContextSensitiveTest;

import ca.uhn.hl7v2.HL7Exception;
import ca.uhn.hl7v2.model.Message;
import ca.uhn.hl7v2.parser.EncodingNotSupportedException;
import ca.uhn.hl7v2.parser.PipeParser;
import net.sf.saxon.regex.RegexSyntaxException;

/**
 * Message utility test
 * @author Justin
 *
 */
public class MessageUtilTest extends BaseModuleContextSensitiveTest {

	@Before
	public void before()
	{
		Context.getAdministrationService().saveGlobalProperty(new GlobalProperty(MpiClientConfiguration.PROP_NAME_EXTMAP, "Father's Name:NK1-2-2?NK1-3=FTH,Mother's Name:PID-6-2"));
		Context.getAdministrationService().saveGlobalProperty(new GlobalProperty(MpiClientConfiguration.PROP_NAME_USE_OMRS_RELS, "true"));

		PersonAttributeType pat = new PersonAttributeType();
		pat.setFormat("java.lang.String");
		pat.setName("Father's Name");
		pat.setDescription("Father's name");
		if(Context.getPersonService().getPersonAttributeTypeByName("Father's Name") == null)
			Context.getPersonService().savePersonAttributeType(pat);
		pat = new PersonAttributeType();
		pat.setFormat("java.lang.String");
		pat.setName("Mother's Name");
		pat.setDescription("Mothers's name");
		if(Context.getPersonService().getPersonAttributeTypeByName("Mother's Name") == null)
			Context.getPersonService().savePersonAttributeType(pat);
		PatientIdentifierType pit = new PatientIdentifierType();
		pit.setName("Test 1");
		pit.setDescription("A Test");
		Context.getPatientService().savePatientIdentifierType(pit);
		pit = new PatientIdentifierType();
		pit.setName("Test 2");
		pit.setDescription("A Test");
		Context.getPatientService().savePatientIdentifierType(pit);
		pit = new PatientIdentifierType();
		pit.setName("Test 3");
		pit.setDescription("A Test");
		Context.getPatientService().savePatientIdentifierType(pit);
		
		Context.getAdministrationService().saveGlobalProperty(new GlobalProperty(MpiClientConfiguration.PROP_NAME_ID_EXPORT_TYPE, "Test 1=1.2.3.4.5,Test 2=1.2.3.4.5.65.6.7,Test 3=FOO"));

	}
	
	/**
	 * Test the create PDQ message 
	 * @throws HL7Exception 
	 */
	@Test
	public void testCreatePdqMessageName() throws HL7Exception {
		MessageUtil util = MessageUtil.getInstance();
		Message pdqMessage = util.createPdqMessage(new HashMap<String, String>() {{
			put("@PID.5.1", "SMITH");
			put("@PID.5.2", "JOHN");
		}});
		String message = new PipeParser().encode(pdqMessage);
		assertTrue("Must have @PID.5.1^SMITH", message.contains("@PID.5.1^SMITH"));
		assertTrue("Must have @PID.5.2^JOHN", message.contains("@PID.5.2^JOHN"));
	}
	
	/**
	 * Test the create PDQ message 
	 * @throws HL7Exception 
	 * @throws ParseException 
	 */
	@Test
	public void testCreatePdqMessageDob() throws HL7Exception, ParseException {
		MessageUtil util = MessageUtil.getInstance();
		Context.getAdministrationService().saveGlobalProperty(new GlobalProperty(MpiClientConfiguration.PROP_SEARCH_DATE_FUZZ, "1"));
		final Date dobDate = new SimpleDateFormat("yyyyMMdd".substring(0, 4)).parse("1996");
		
		Message pdqMessage = util.createPdqMessage(new HashMap<String, String>() {{
			put("@PID.7", "1995~1996~1997");
		}});
		String message = new PipeParser().encode(pdqMessage);
		assertTrue("Must have @PID.7^1996", message.contains("@PID.7^1996"));
		assertTrue("Must have @PID.7^1997", message.contains("@PID.7^1997"));
		assertTrue("Must have @PID.7^1995", message.contains("@PID.7^1995"));
	}
	
	/**
	 * Test the create PDQ message 
	 * @throws HL7Exception 
	 */
	@Test
	public void testCreatePdqMessageNameGender() throws HL7Exception {
		MessageUtil util = MessageUtil.getInstance();
		Message pdqMessage = util.createPdqMessage(new HashMap<String, String>() {{
			put("@PID.5.1", "SMITH");
			put("@PID.5.2", "JOHN");
			put("@PID.7", "M");
		}});
		String message = new PipeParser().encode(pdqMessage);
		assertTrue("Must have @PID.5.1^SMITH", message.contains("@PID.5.1^SMITH"));
		assertTrue("Must have @PID.5.2^JOHN", message.contains("@PID.5.2^JOHN"));
		assertTrue("Must have @PID.7^M", message.contains("@PID.7^M"));
	}

	/**
	 * Create an Admit Message
	 * @throws HL7Exception 
	 * @throws RegexSyntaxException 
	 */
	@Test
	public void testCreateAdmit() throws HL7Exception, RegexSyntaxException {
		Patient testPatient = new Patient();
		testPatient.setGender("F");
		testPatient.setBirthdate(new Date());
		testPatient.addName(new PersonName("John", "T", "Smith"));
		testPatient.getNames().iterator().next().setPreferred(true);
		PatientIdentifierType pit = Context.getPatientService().getPatientIdentifierTypeByName("Test 2");
		testPatient.addIdentifier(new PatientIdentifier("123", pit, Context.getLocationService().getDefaultLocation()));
		pit = Context.getPatientService().getPatientIdentifierTypeByName("Test 3");
		testPatient.addIdentifier(new PatientIdentifier("AD3", pit, Context.getLocationService().getDefaultLocation()));
		testPatient.setId(1203);
		
		Message admit = MessageUtil.getInstance().createAdmit(testPatient);
		String message = new PipeParser().encode(admit);
		Assert.assertTrue("Expected 123^^^&1.2.3.4.5.65.6.7&ISO", message.contains("123^^^&1.2.3.4.5.65.6.7&ISO"));
		Assert.assertTrue("Expected AD3^^^FOO", message.contains("AD3^^^FOO"));
		Assert.assertTrue("Expected Smith^John^T^^^^L", message.contains("Smith^John^T^^^^L"));
	}
	
	/**
	 * Create an Admit Message
	 * @throws HL7Exception 
	 * @throws RegexSyntaxException 
	 */
	@Test
	public void testCreateNextOfKin() throws HL7Exception, RegexSyntaxException {
		Patient testPatient = new Patient();
		testPatient.setGender("F");
		testPatient.setBirthdate(new Date());
		testPatient.addName(new PersonName("John", "T", "Smith"));
		testPatient.getNames().iterator().next().setPreferred(true);
		PatientIdentifierType pit = Context.getPatientService().getPatientIdentifierTypeByName("Test 2");
		testPatient.addIdentifier(new PatientIdentifier("123", pit, Context.getLocationService().getDefaultLocation()));
		pit = Context.getPatientService().getPatientIdentifierTypeByName("Test 3");
		testPatient.addIdentifier(new PatientIdentifier("AD3", pit, Context.getLocationService().getDefaultLocation()));
		testPatient.getPatientIdentifier().setPreferred(true);
		testPatient = Context.getPatientService().savePatient(testPatient);
		// Relationship
		Relationship relationship = new Relationship();
		RelationshipType relType = Context.getPersonService().getRelationshipTypeByName("Parent/Child");
		relationship.setRelationshipType(relType);
		
		Person relative = new Person();
		relative.addName(new PersonName("MARY", null, "SMITH"));
		relative.setGender("F");
		
		relationship.setPersonA(relative);
		relationship.setPersonB(testPatient);
		Context.getPersonService().savePerson(relative);
		Context.getPersonService().saveRelationship(relationship);
		
		Message admit = MessageUtil.getInstance().createAdmit(testPatient);
		String message = new PipeParser().encode(admit);
		Assert.assertTrue("Expected 123^^^&1.2.3.4.5.65.6.7&ISO", message.contains("123^^^&1.2.3.4.5.65.6.7&ISO"));
		Assert.assertTrue("Expected AD3^^^FOO", message.contains("AD3^^^FOO"));
		Assert.assertTrue("Expected Smith^John^T^^^^L", message.contains("Smith^John^T^^^^L"));
		Assert.assertTrue("Expected PAR in NK1", message.contains("PAR"));
	}

	/**
	 * Create an Admit Message
	 * @throws HL7Exception 
	 * @throws RegexSyntaxException 
	 */
	@Test
	public void testCreateExtension() throws HL7Exception, RegexSyntaxException {
		Patient testPatient = new Patient();
		testPatient.setGender("F");
		testPatient.setBirthdate(new Date());
		testPatient.addName(new PersonName("John", "T", "Smith"));
		testPatient.getNames().iterator().next().setPreferred(true);
		PatientIdentifierType pit = Context.getPatientService().getPatientIdentifierTypeByName("Test 2");
		testPatient.addIdentifier(new PatientIdentifier("123", pit, Context.getLocationService().getDefaultLocation()));
		pit = Context.getPatientService().getPatientIdentifierTypeByName("Test 3");
		testPatient.addIdentifier(new PatientIdentifier("AD3", pit, Context.getLocationService().getDefaultLocation()));
		testPatient.getPatientIdentifier().setPreferred(true);
		testPatient.addAttribute(new PersonAttribute(
				Context.getPersonService().getPersonAttributeTypeByName("Father's Name"),
				"JUSTIN FYFE"
		));
		testPatient = Context.getPatientService().savePatient(testPatient);
		
		Message admit = MessageUtil.getInstance().createAdmit(testPatient);
		String message = new PipeParser().encode(admit);
		Assert.assertTrue("Expected 123^^^&1.2.3.4.5.65.6.7&ISO", message.contains("123^^^&1.2.3.4.5.65.6.7&ISO"));
		Assert.assertTrue("Expected AD3^^^FOO", message.contains("AD3^^^FOO"));
		Assert.assertTrue("Expected Smith^John^T^^^^L", message.contains("Smith^John^T^^^^L"));
		Assert.assertTrue("Expected PAR in NK1", message.contains("FTH"));
	}
	
	/**
	 * Create an Admit Message
	 * @throws HL7Exception 
	 * @throws RegexSyntaxException 
	 */
	@Test
	public void testCreateTwoExtension() throws HL7Exception, RegexSyntaxException {
		

		Patient testPatient = new Patient();
		testPatient.setGender("F");
		testPatient.setBirthdate(new Date());
		testPatient.addName(new PersonName("John", "T", "Smith"));
		testPatient.getNames().iterator().next().setPreferred(true);
		PatientIdentifierType pit = Context.getPatientService().getPatientIdentifierTypeByName("Test 2");
		testPatient.addIdentifier(new PatientIdentifier("123", pit, Context.getLocationService().getDefaultLocation()));
		pit = Context.getPatientService().getPatientIdentifierTypeByName("Test 3");
		testPatient.addIdentifier(new PatientIdentifier("AD3", pit, Context.getLocationService().getDefaultLocation()));
		testPatient.getPatientIdentifier().setPreferred(true);
		testPatient.addAttribute(new PersonAttribute(
				Context.getPersonService().getPersonAttributeTypeByName("Father's Name"),
				"JACK SMITH"
		));
		testPatient.addAttribute(new PersonAttribute(
				Context.getPersonService().getPersonAttributeTypeByName("Mother's Name"),
				"ALLISON SMITH"
		));
		testPatient = Context.getPatientService().savePatient(testPatient);
		
		Message admit = MessageUtil.getInstance().createAdmit(testPatient);
		String message = new PipeParser().encode(admit);
		Assert.assertTrue("Expected 123^^^&1.2.3.4.5.65.6.7&ISO", message.contains("123^^^&1.2.3.4.5.65.6.7&ISO"));
		Assert.assertTrue("Expected AD3^^^FOO", message.contains("AD3^^^FOO"));
		Assert.assertTrue("Expected Smith^John^T^^^^L", message.contains("Smith^John^T^^^^L"));
		Assert.assertTrue("Expected FTH in NK1", message.contains("FTH"));
		Assert.assertTrue("Expected Mother's name in PID", message.contains("ALLISON"));
	}
	

	/**
	 * Create an Admit Message
	 * @throws HL7Exception 
	 * @throws RegexSyntaxException 
	 */
	@Test
	public void testCreateNameRewrite() throws HL7Exception, RegexSyntaxException {
		

		Patient testPatient = new Patient();
		testPatient.setGender("F");
		testPatient.setBirthdate(new Date());
		testPatient.addName(new PersonName("Ma Soe Moe Myat",null, "(NULL)"));
		testPatient.getNames().iterator().next().setPreferred(true);
		PatientIdentifierType pit = Context.getPatientService().getPatientIdentifierTypeByName("Test 2");
		testPatient.addIdentifier(new PatientIdentifier("123", pit, Context.getLocationService().getDefaultLocation()));
		pit = Context.getPatientService().getPatientIdentifierTypeByName("Test 3");
		testPatient.addIdentifier(new PatientIdentifier("AD3", pit, Context.getLocationService().getDefaultLocation()));
		testPatient.getPatientIdentifier().setPreferred(true);
		testPatient.addAttribute(new PersonAttribute(
				Context.getPersonService().getPersonAttributeTypeByName("Father's Name"),
				"Mg Wao Phyo"
		));

		testPatient.getPatientIdentifier().setPreferred(true);
		testPatient = Context.getPatientService().savePatient(testPatient);
		// Relationship
		Relationship relationship = new Relationship();
		RelationshipType relType = Context.getPersonService().getRelationshipTypeByName("Parent/Child");
		relationship.setRelationshipType(relType);
		
		Person relative = new Person();
		relative.addName(new PersonName("Ma Ni Ni Myat", null, "(NULL)"));
		relative.getNames().iterator().next().setPreferred(true);
		relative.setGender("F");
		
		relationship.setPersonA(relative);
		relationship.setPersonB(testPatient);
		Context.getPersonService().savePerson(relative);
		Context.getPersonService().saveRelationship(relationship);
		
		
		Context.getAdministrationService().saveGlobalProperty(new GlobalProperty(MpiClientConfiguration.PROP_NAME_PAT_NAME_REWRITE, "/^(?:(ma|mg|mrs?|ms|dr|daw)\\s)?([A-Za-z\\s]*?)(?:\\s\\(NULL\\))?$/^$2^^^$1^^^A/i"));
		MpiClientConfiguration.getInstance().clearCache();
		
		Message admit = MessageUtil.getInstance().createAdmit(testPatient);
		String message = new PipeParser().encode(admit);
		Assert.assertTrue("Expected ^Soe Moe Myat^^^Ma^^L^A", message.contains("^Soe Moe Myat^^^Ma^^L^A"));
		Assert.assertTrue("Expected ^Wao Phyo^^^Mg^^^^A", message.contains("^Wao Phyo^^^Mg^^^A"));
		Assert.assertTrue("Expected ^Ni Ni Myat^^^Ma^^^L^A", message.contains("^Ni Ni Myat^^^Ma^^L^A"));
		Context.getAdministrationService().saveGlobalProperty(new GlobalProperty(MpiClientConfiguration.PROP_NAME_PAT_NAME_REWRITE, ""));
		MpiClientConfiguration.getInstance().clearCache();
	}
	
	/**
	 * Tests that the preferred correlation works
	 * @throws HL7Exception 
	 * @throws RegexSyntaxException 
	 */
	@Test
	public void testDoesNotIncludeLocalIdentifier() throws HL7Exception, RegexSyntaxException {
		

		Patient testPatient = new Patient();
		testPatient.setGender("M");
		testPatient.setBirthdate(new Date());
		testPatient.addName(new PersonName("No Local",null, "(NULL)"));
		testPatient.getNames().iterator().next().setPreferred(true);
		PatientIdentifierType pit = Context.getPatientService().getPatientIdentifierTypeByName("Test 1");
		testPatient.addIdentifier(new PatientIdentifier("564", pit, Context.getLocationService().getDefaultLocation()));
		pit = Context.getPatientService().getPatientIdentifierTypeByName("Test 2");
		testPatient.addIdentifier(new PatientIdentifier("AD9", pit, Context.getLocationService().getDefaultLocation()));
		testPatient.getPatientIdentifier().setPreferred(true);

		testPatient.getPatientIdentifier().setPreferred(true);
		testPatient = Context.getPatientService().savePatient(testPatient);

		String oldLocal = MpiClientConfiguration.getInstance().getLocalPatientIdRoot();

		// message should have local domain
		Message admit = MessageUtil.getInstance().createAdmit(testPatient);
		String message = new PipeParser().encode(admit);
		Assert.assertTrue("Expected message to have LOCAL domain", message.contains("^" + oldLocal));

		Context.getAdministrationService().saveGlobalProperty(new GlobalProperty(MpiClientConfiguration.PROP_NAME_LOCAL_ID, "-"));
		Context.getAdministrationService().saveGlobalProperty(new GlobalProperty(MpiClientConfiguration.PROP_NAME_PREFER_CORR_AA, "TEST"));
		MpiClientConfiguration.getInstance().clearCache();
		
		admit = MessageUtil.getInstance().createAdmit(testPatient);
		message = new PipeParser().encode(admit);
		Assert.assertFalse("Expected message to not have LOCAL domain", message.contains("^" + oldLocal));

		Context.getAdministrationService().saveGlobalProperty(new GlobalProperty(MpiClientConfiguration.PROP_NAME_LOCAL_ID, oldLocal));
		Context.getAdministrationService().saveGlobalProperty(new GlobalProperty(MpiClientConfiguration.PROP_NAME_PREFER_CORR_AA, ""));
		MpiClientConfiguration.getInstance().clearCache();
	}
	
	/**
	 * Tests that the system can resolve multiple domain identities
	 */
	@Test
	public void testCanResolveMultiDomains() throws HL7Exception, RegexSyntaxException {
		
		try {
			Context.getAdministrationService().saveGlobalProperty(new GlobalProperty(MpiClientConfiguration.PROP_NAME_PREFER_CORR_AA, "1.2.3.4.5"));
			MpiClientConfiguration.getInstance().clearCache();
		
			// Register patient 
			Patient testPatient = new Patient();
			testPatient.setGender("F");
			testPatient.setBirthdate(new Date());
			testPatient.addName(new PersonName("JENNIFER",null, "SMITH"));
			testPatient.getNames().iterator().next().setPreferred(true);
			PatientIdentifierType pit = Context.getPatientService().getPatientIdentifierTypeByName("Test 1");
			testPatient.addIdentifier(new PatientIdentifier("RJ-9991", pit, Context.getLocationService().getDefaultLocation()));
			testPatient.getPatientIdentifier().setPreferred(true);
			
	
			// Save
			Context.getPatientService().savePatient(testPatient);
			
			// Verify domains can be resolved when passing in by code
			Message pixQuery = MessageUtil.getInstance().createPixMessage(testPatient, "DOMAIN1", "DOMAIN2");
			String messageEr7 = new PipeParser().encode(pixQuery);
			Assert.assertTrue("Message should contain domain 1", messageEr7.contains("^^^DOMAIN1"));
			Assert.assertTrue("Message should contain domain 2", messageEr7.contains("^^^DOMAIN2"));
			
			// Verify domains can be resolve passing in array
			String[] domains = new String[] { "DOMAIN1", "DOMAIN2" };
			pixQuery = MessageUtil.getInstance().createPixMessage(testPatient, domains);
			messageEr7 = new PipeParser().encode(pixQuery);
			Assert.assertTrue("Message should contain domain 1", messageEr7.contains("^^^DOMAIN1"));
			Assert.assertTrue("Message should contain domain 2", messageEr7.contains("^^^DOMAIN2"));
		}
		finally {
			Context.getAdministrationService().saveGlobalProperty(new GlobalProperty(MpiClientConfiguration.PROP_NAME_PREFER_CORR_AA, ""));
			MpiClientConfiguration.getInstance().clearCache();

		}
	}
	
	/**
	 * Tests that the correlation routine works properly
	 */
	@Test
	public void testCanResolveLocalPatientIdentifier() throws HL7Exception, RegexSyntaxException {
		
		// Message containing TEST domain
		String aMessageWithPID = "MSH|^~\\&|CR1^^|MOH_CAAT^^|TEST_HARNESS^^|TEST^^|20141104174451||RSP^K23^RSP_K21|TEST-CR-05-10|P|2.5\r" + 
				"PID|||RJ-1999^^^TEST&1.2.3.4.5&ISO||SMITH^JENNIFER^^^^^L|SMITH^^^^^^L|19840125|F|||123 Main Street West ^^NEWARK^NJ^30293||^PRN^PH^^^409^3049506||||||";

		// Register patient 
		Patient testPatient = new Patient();
		testPatient.setGender("M");
		testPatient.setBirthdate(new Date());
		testPatient.addName(new PersonName("JENNIFER",null, "SMITH"));
		testPatient.getNames().iterator().next().setPreferred(true);
		PatientIdentifierType pit = Context.getPatientService().getPatientIdentifierTypeByName("Test 1");
		testPatient.addIdentifier(new PatientIdentifier("RJ-1999", pit, Context.getLocationService().getDefaultLocation()));
		testPatient.getPatientIdentifier().setPreferred(true);
		
		// Save
		Context.getPatientService().savePatient(testPatient);
		
		// Now parse the message
		Message mut = new PipeParser().parse(aMessageWithPID);
		try {
			Context.getAdministrationService().saveGlobalProperty(new GlobalProperty(MpiClientConfiguration.PROP_NAME_PREFER_CORR_AA, "1.2.3.4.5"));
			MpiClientConfiguration.getInstance().clearCache();
			List<MpiPatient> pat = MessageUtil.getInstance().interpretPIDSegments(mut);
			Assert.assertEquals(1, pat.size());
			Assert.assertEquals(testPatient.getId(), pat.get(0).getId());
			
			// Now testthat the resolution message is appropriate
			Message pixQuery = MessageUtil.getInstance().createPixMessage(testPatient, "NHID");
			String messageRaw = new PipeParser().encode(pixQuery);
			Assert.assertFalse("PIX message should not contain local ID", messageRaw.contains(testPatient.getId().toString()));
			Assert.assertTrue("PIX message should contain the preferred correlation ID", messageRaw.contains("RJ-1999"));
		} catch (MpiClientException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} finally {
			Context.getAdministrationService().saveGlobalProperty(new GlobalProperty(MpiClientConfiguration.PROP_NAME_PREFER_CORR_AA, ""));
			MpiClientConfiguration.getInstance().clearCache();
		}
		
	}
	
	/**
	 * Test the interpretation of the PID segment
	 * @throws HL7Exception 
	 * @throws EncodingNotSupportedException 
	 */
	@Test
	public void testInterpretPidSimple() throws EncodingNotSupportedException, HL7Exception
	{
		String aMessageWithPID = "MSH|^~\\&|CR1^^|MOH_CAAT^^|TEST_HARNESS^^|TEST^^|20141104174451||RSP^K23^RSP_K21|TEST-CR-05-10|P|2.5\r" + 
								"PID|||RJ-439^^^TEST&1.2.3.4.5&ISO||JONES^JENNIFER^^^^^L|SMITH^^^^^^L|19840125|F|||123 Main Street West ^^NEWARK^NJ^30293||^PRN^PH^^^409^3049506||||||";
		
		Message mut = new PipeParser().parse(aMessageWithPID);
		try {
			List<MpiPatient> pat = MessageUtil.getInstance().interpretPIDSegments(mut);
			Assert.assertEquals(1, pat.size());
			Assert.assertEquals("RJ-439", pat.get(0).getIdentifiers().iterator().next().getIdentifier());
			Assert.assertEquals("Test 1", pat.get(0).getIdentifiers().iterator().next().getIdentifierType().getName());
			Assert.assertEquals("F", pat.get(0).getGender());
			Assert.assertEquals(false, pat.get(0).getBirthdateEstimated());
		} catch (MpiClientException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
	}
	
	/**
	 * Test the interpretation of the PID segment
	 * @throws HL7Exception 
	 * @throws EncodingNotSupportedException 
	 */
	@Test
	public void testInterpretPidNextOfKin() throws EncodingNotSupportedException, HL7Exception
	{
		String aMessageWithPID = "MSH|^~\\&|CR1^^|MOH_CAAT^^|TEST_HARNESS^^|TEST^^|20141104174451||RSP^K23^RSP_K21|TEST-CR-05-10|P|2.5\r" + 
								"PID|||RJ-439^^^TEST&1.2.3.4.5&ISO||JONES^JENNIFER^^^^^L|SMITH^JENNIFER^^^^^L|19840125|F|||123 Main Street West ^^NEWARK^NJ^30293||^PRN^PH^^^409^3049506||||||\r" +
								"NK1||SMITH^JENNIFER^^^^^L|MTH\r" +
								"NK1||SMITH^JOHN|FTH";
		

		Message mut = new PipeParser().parse(aMessageWithPID);
		try {
			List<MpiPatient> pat = MessageUtil.getInstance().interpretPIDSegments(mut);
			Assert.assertEquals(1, pat.size());
			Assert.assertEquals("RJ-439", pat.get(0).getIdentifiers().iterator().next().getIdentifier());
			Assert.assertEquals("Test 1", pat.get(0).getIdentifiers().iterator().next().getIdentifierType().getName());
			Assert.assertEquals("F", pat.get(0).getGender());
			Assert.assertEquals(2, pat.get(0).getRelationships().size());
			Assert.assertEquals(2, pat.get(0).getAttributes().size());
			Assert.assertEquals(false, pat.get(0).getBirthdateEstimated());
		} catch (MpiClientException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
	}
	
	/**
	 * Test the interpretation of the PID segment
	 * @throws HL7Exception 
	 * @throws EncodingNotSupportedException 
	 */
	@Test
	public void testInterpretPidMultiIdentifier() throws EncodingNotSupportedException, HL7Exception
	{
		String aMessageWithPID = "MSH|^~\\&|CR1^^|MOH_CAAT^^|TEST_HARNESS^^|TEST^^|20141104174451||RSP^K23^RSP_K21|TEST-CR-05-10|P|2.5\r" + 
								"PID|||RJ-439^^^TEST&1.2.3.4.5&ISO~TEST-222^^^FOO||JONES^JENNIFER^^^^^L|SMITH^^^^^^L|19840125|F|||123 Main Street West ^^NEWARK^NJ^30293||^PRN^PH^^^409^3049506||||||";
		
		Message mut = new PipeParser().parse(aMessageWithPID);
		try
		{
			List<MpiPatient> pat = MessageUtil.getInstance().interpretPIDSegments(mut);
			Assert.assertEquals(1, pat.size());
			Assert.assertEquals(2, pat.get(0).getIdentifiers().size());
		} catch (MpiClientException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	/**
	 * Test the interpretation of the PID segment
	 * @throws HL7Exception 
	 * @throws EncodingNotSupportedException 
	 */
	@Test
	public void testInterpretPidMultiResults() throws EncodingNotSupportedException, HL7Exception
	{
		String aMessageWithPID = "MSH|^~\\&|CR1^^|MOH_CAAT^^|TEST_HARNESS^^|TEST^^|20141104174451||RSP^K23^RSP_K21|TEST-CR-05-10|P|2.5\r" + 
								"PID|||RJ-439^^^TEST&1.2.3.4.5&ISO||JONES^JENNIFER^^^^^L|SMITH^JENNY^^^^^L|19840125|F|||123 Main Street West ^^NEWARK^NJ^30293||^PRN^PH^^^409^3049506||||||\r" +
								"PID|||RJ-442^^^FOO||FOSTER^FANNY^FULL^^^^L|FOSTER^MARY^^^^^L|1970|F|||123 W34 St^^FRESNO^CA^3049506||^PRN^PH^^^419^31495|^^PH^^^034^059434|EN|S|||||\r";
		
		Message mut = new PipeParser().parse(aMessageWithPID);
		try
		{
			List<MpiPatient> pat = MessageUtil.getInstance().interpretPIDSegments(mut);
			Assert.assertEquals(2, pat.size());
			Assert.assertEquals(1, pat.get(0).getAttributes().size());
		} catch (MpiClientException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

}
