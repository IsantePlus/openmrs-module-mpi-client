package org.openmrs.module.santedb.mpiclient.api;

import org.dcm4che3.net.audit.AuditLogger;
import org.openmrs.api.OpenmrsService;

/**
 * Audit service
 */
public interface AtnaAuditService extends OpenmrsService{
	
	
	/**
	 * Get the audit logger
	 */
	public AuditLogger getLogger();
	
}
