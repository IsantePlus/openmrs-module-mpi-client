package org.openmrs.module.santedb.mpiclient.util;

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.when;

import org.hl7.fhir.r4.model.Patient;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import org.openmrs.module.fhir2.api.translators.PatientTranslator;
import org.openmrs.module.santedb.mpiclient.model.MpiPatient;

@RunWith(MockitoJUnitRunner.class)
public class FhirUtilTest {

	private FhirUtil fhirUtil;

	@Mock
	private PatientTranslator patientTranslator;

	@Before
	public void setup() {
		fhirUtil = FhirUtil.getInstance();
		fhirUtil.setPatientTranslator(patientTranslator);
	}

	@Test
	public void testParseFhirPatient_shouldCreateEmptyPatient() {
		Patient emptyPatient = new Patient();
		org.openmrs.Patient omrsPatient = new org.openmrs.Patient();

		when(patientTranslator.toOpenmrsType(any())).thenReturn(omrsPatient);

		MpiPatient result = fhirUtil.parseFhirPatient(emptyPatient, omrsPatient);

		Assert.assertTrue(result != null);
	}
}
