package org.openmrs.module.santedb.mpiclient.aop;

import java.lang.reflect.Method;
import java.util.Date;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.openmrs.Patient;
import org.openmrs.PatientIdentifier;
import org.openmrs.api.PatientService;
import org.openmrs.api.context.Context;
import org.openmrs.module.santedb.mpiclient.api.MpiClientService;
import org.openmrs.module.santedb.mpiclient.configuration.MpiClientConfiguration;
import org.openmrs.module.santedb.mpiclient.exception.MpiClientException;
import org.springframework.aop.AfterReturningAdvice;

/**
 * After returning from the save method of the Patient service
 */
public class PatientUpdateAdvice implements AfterReturningAdvice {
	
	private final Log log = LogFactory.getLog(this.getClass());

	private final MpiClientConfiguration m_configuration = MpiClientConfiguration.getInstance();
	
	/**
	 * Runs everytime a patient a updated
	 * @see org.springframework.aop.AfterReturningAdvice#afterReturning(java.lang.Object, java.lang.reflect.Method, java.lang.Object[], java.lang.Object)
	 */
	public void afterReturning(Object returnValue, Method method, Object[] args, Object target) throws Throwable {
		if(method.getName().equals("savePatient") && target instanceof PatientService)
		{
			log.debug("Sending update to the MPI for new patient data...");
			try
			{
				Patient patient = (Patient)returnValue;
				// Does this patient have an ECID? 
				boolean hasEcid = false;
				for(PatientIdentifier pid : patient.getIdentifiers())
					hasEcid |= pid.getIdentifierType().getName().equals(this.m_configuration.getEnterprisePatientIdRoot()) ||
							pid.getIdentifierType().getUuid().equals(this.m_configuration.getEnterprisePatientIdRoot());
						
				MpiClientService hieService = Context.getService(MpiClientService.class);
				if(hasEcid && patient.getDateChanged() != null) // notify update
				{
					hieService.updatePatient(patient);
					hieService.synchronizePatientEnterpriseId(patient);
				}
				else if(!hasEcid) // create case
				{
					hieService.exportPatient(patient);
					PatientIdentifier pid = hieService.resolvePatientIdentifier(patient, this.m_configuration.getEnterprisePatientIdRoot());
					patient.addIdentifier(pid);
					patient.setDateChanged(new Date());
					Context.getPatientService().savePatient(patient);
				}
			}
			catch(MpiClientException e)
			{
				log.error(e);
			}
		}
	}
	
}
