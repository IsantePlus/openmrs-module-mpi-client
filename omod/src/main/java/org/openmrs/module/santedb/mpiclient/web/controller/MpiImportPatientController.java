package org.openmrs.module.santedb.mpiclient.web.controller;

import java.text.ParseException;
import java.util.Date;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.openmrs.Patient;
import org.openmrs.PatientIdentifier;
import org.openmrs.Visit;
import org.openmrs.api.context.Context;
import org.openmrs.module.santedb.mpiclient.api.MpiClientService;
import org.openmrs.module.santedb.mpiclient.configuration.MpiClientConfiguration;
import org.openmrs.module.santedb.mpiclient.exception.MpiClientException;
import org.openmrs.module.santedb.mpiclient.model.MpiPatient;
import org.openmrs.module.santedb.mpiclient.web.model.PatientSearchModel;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.ModelAndView;

@Controller
@RequestMapping("/module/santedb-mpiclient/mpiImportPatient")
public class MpiImportPatientController {

	protected final Log log = LogFactory.getLog(this.getClass());
	/**
	 * Handle the get operation
	 * @param model
	 */
	@RequestMapping(method = RequestMethod.GET)
	public ModelAndView index(ModelMap model,  @RequestParam(value = "ecid") String ecid) {
		
		
		if(ecid == null)
			throw new IllegalArgumentException("ecid must be supplied");
		
		try
		{
			MpiClientService service = Context.getService(MpiClientService.class);
			MpiClientConfiguration config = MpiClientConfiguration.getInstance();
			// Get rid of the ecid from the model
			Patient patient = service.getPatient(ecid, config.getEnterprisePatientIdRoot());
			
			
			PatientIdentifier nullPid = null;
			for(PatientIdentifier pid : patient.getIdentifiers())
				if(pid.getIdentifierType() == null)
					nullPid = pid;
			if(nullPid != null)
				patient.removeIdentifier(nullPid);
			
			model.put("patient", patient);
			return new ModelAndView("/module/santedb-mpiclient/mpiImportPatient", model);
		}
		catch(MpiClientException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			model.put("error", e.getMessage());
			return new ModelAndView("/module/santedb-mpiclient/mpiImportPatient", model);
		}
	}
	
	/**
	 * Handle the post
	 * @return 
	 * @throws ParseException 
	 */
	@RequestMapping(method = RequestMethod.POST)
	public ModelAndView doImport(Map<String, Object> model, @ModelAttribute("importPatient") PatientSearchModel search, @RequestParam(value = "ecid") String ecid) throws ParseException
	{
		if(ecid == null)
			throw new IllegalArgumentException("ecid must be supplied");
		
		try
		{
			// Service for the HIE
			MpiClientService service = Context.getService(MpiClientService.class);
			MpiClientConfiguration config = MpiClientConfiguration.getInstance();
			MpiPatient pat = service.getPatient(ecid, config.getEnterprisePatientIdRoot());
			Patient createdPat = service.importPatient(pat);
			
			// HACK: Create a visit and encounter
			Visit visit = new Visit();
			visit.setPatient(createdPat);
			visit.setVisitType(Context.getVisitService().getVisitType(1));
			visit.setStartDatetime(new Date());
			visit.setLocation(Context.getLocationService().getDefaultLocation());
			Context.getVisitService().saveVisit(visit);
			return new ModelAndView("redirect:/kenyaemr/registration/registrationViewPatient.page?patientId=" + createdPat.getId().toString() );
		}
		catch(MpiClientException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			model.put("error", e.getMessage());
			return new ModelAndView("/module/santedb-mpiclient/mpiImportPatient", model);
		}
	}
}
