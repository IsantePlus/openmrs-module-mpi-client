/**
 * Portions Copyright 2015-2018 Mohawk College of Applied Arts and Technology
 * Portions Copyright (c) 2014-2020 Fyfe Software Inc.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License"); you
 * may not use this file except in compliance with the License. You may
 * obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package org.openmrs.module.santedb.mpiclient.aop;


import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.openmrs.Encounter;
import org.openmrs.Patient;
import org.openmrs.api.EncounterService;
import org.openmrs.api.PatientService;
import org.openmrs.api.context.Context;
import org.openmrs.module.santedb.mpiclient.configuration.MpiClientConfiguration;
import org.openmrs.module.santedb.mpiclient.model.MpiPatientExport;
import org.springframework.aop.AfterReturningAdvice;

import java.lang.reflect.Method;
import java.util.concurrent.TimeUnit;

/**
 * After returning from the save method of the Patient service
 */
public class EncounterSynchronizationAdvice implements AfterReturningAdvice {


    private final Log log = LogFactory.getLog(this.getClass());

    private final MpiClientConfiguration m_configuration = MpiClientConfiguration.getInstance();

    /**
     * Runs everytime a patient a updated
     * @see AfterReturningAdvice#afterReturning(Object, Method, Object[], Object)
     */
    public void afterReturning(Object returnValue, Method method, Object[] args, Object target) throws Throwable {
        if (method.getName().equals("saveEncounter") && target instanceof EncounterService) {
            org.openmrs.Encounter encounter = (Encounter) returnValue;
            if(encounter.getObsAtTopLevel(false).size() > 0){
                try {
                    TimeUnit.SECONDS.sleep(15);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                }
                MpiPatientExport patientExport = new MpiPatientExport(encounter.getPatient(), null, null, null, encounter.getObsAtTopLevel(false));
                PatientUpdateWorker worker = new PatientUpdateWorker(patientExport, Context.getUserContext());
                worker.start();
            }
        }
    }

}
