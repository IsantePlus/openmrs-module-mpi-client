package org.openmrs.module.santedb.mpiclient.api.impl;

import java.util.Date;
import java.util.List;

import org.dcm4che3.net.audit.AuditLogger;
import org.openmrs.Patient;
import org.openmrs.PatientIdentifier;
import org.openmrs.module.santedb.mpiclient.api.MpiClientService;
import org.openmrs.module.santedb.mpiclient.exception.MpiClientException;
import org.openmrs.module.santedb.mpiclient.model.MpiPatient;

/**
 * MPI Client Service Implementation using FHIR
 * @author fyfej
 *
 */
public class FhirMpiClientServiceImpl implements MpiClientService {

	@Override
	public void onStartup() {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void onShutdown() {
		// TODO Auto-generated method stub
		
	}

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

	@Override
	public void exportPatient(Patient patient) throws MpiClientException {
		// TODO Auto-generated method stub
		
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
