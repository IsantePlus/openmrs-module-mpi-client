package org.openmrs.module.santedb.mpiclient.web.controller;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.openmrs.PatientIdentifier;
import org.openmrs.api.context.Context;
import org.openmrs.module.santedb.mpiclient.api.MpiClientService;
import org.openmrs.module.santedb.mpiclient.exception.MpiClientException;
import org.openmrs.module.santedb.mpiclient.model.MpiPatient;
import org.openmrs.module.santedb.mpiclient.web.model.PatientResultModel;
import org.openmrs.module.santedb.mpiclient.web.model.PatientSearchModel;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.SessionAttributes;
import org.springframework.web.servlet.ModelAndView;

/**
 * Find Patient in the HIE Controller
 * @author Justin
 *
 */
@Controller
@RequestMapping("/module/santedb-mpiclient/mpiFindPatient")
@SessionAttributes("patientSearch")
public class MpiFindPatientController {

	protected final Log log = LogFactory.getLog(this.getClass());
	/**
	 * Handle the get operation
	 * @param model
	 */
	@RequestMapping(method = RequestMethod.GET)
	public void index(ModelMap model) {
		model.put("patientSearch", new PatientSearchModel());
	}
	
	/**
	 * Handle the post
	 * @return 
	 * @throws ParseException 
	 */
	@RequestMapping(method = RequestMethod.POST)
	public ModelAndView doSearch(Map<String, Object> model, @ModelAttribute("patientSearch") PatientSearchModel search) throws ParseException
	{
		
		try {
			
			// Service for the HIE
			MpiClientService service = Context.getService(MpiClientService.class);
			Date dobDate = null;
			boolean isFuzzy = false;
			PatientIdentifier identifier = null,
					momsIdentifier = null;
			
			// Date format
			if(search.getDateOfBirth() != null && !search.getDateOfBirth().isEmpty())
			{
				dobDate = new SimpleDateFormat("yyyyMMdd".substring(0, search.getDateOfBirth().length())).parse(search.getDateOfBirth());
				isFuzzy = search.getDateOfBirth().length() < 8;
				log.warn(String.format("Using search parameter for date: %s", dobDate.toString()));
				
			}
			if(search.getIdentifier() != null && !search.getIdentifier().isEmpty())
			{
				if(search.getMomsId() != null && search.getMomsId().equals("true"))
					momsIdentifier = new PatientIdentifier(search.getIdentifier(), null, null);
				else
					identifier = new PatientIdentifier(search.getIdentifier(), null, null);
				
			}
			
			List<MpiPatient> results = service.searchPatient(search.getFamilyName(), search.getGivenName(), dobDate, isFuzzy, search.getGender(), null, search.getAddress(),  identifier, momsIdentifier, search.getRelativeName(), search.getBirthPlace());
			List<PatientResultModel> modelResult = new ArrayList<PatientResultModel>();
			for(MpiPatient result : results) {
				modelResult.add(new PatientResultModel(result));
			}
			
			model.put("successful", true);
			model.put("hasResults", modelResult.size() > 0);
			model.put("results", modelResult);
			model.put("patientSearch", search);
			
			return new ModelAndView("/module/santedb-mpiclient/mpiFindPatient", model);
		} catch (MpiClientException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			model.put("successful", false);
			model.put("error", e.getMessage());
			return new ModelAndView("/module/santedb-mpiclient/mpiFindPatient", model);
		}
		
	}
	
}
