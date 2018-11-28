package org.openmrs.module.santedb.mpiclient.configuration;

import java.util.HashMap;
import java.util.Map;

import org.marc.everest.formatters.FormatterUtil;
import org.openmrs.GlobalProperty;
import org.openmrs.api.context.Context;

/**
 * Health information exchange configuration
 * @author Justin
 *
 */
public class MpiClientConfiguration {

	// Lock object
	private static final Object s_lockObject = new Object();
	// Singleton
	private static MpiClientConfiguration s_instance;
	
	public static final String PROP_NAME_PDQ_EP = "mpi-client.endpoint.pdq.addr";
	public static final String PROP_NAME_PDQ_EP_PORT = "mpi-client.endpoint.pdq.port";
	public static final String PROP_NAME_PIX_EP = "mpi-client.endpoint.pix.addr";
	public static final String PROP_NAME_PIX_EP_PORT = "mpi-client.endpoint.pix.port";

	public static final String PROP_NAME_ENT_ID = "mpi-client.pid.enterprise";
	public static final String PROP_NAME_LOCAL_ID = "mpi-client.pid.local";
	public static final String PROP_NAME_PREF_ID = "mpi-client.pid.nhid";
	public static final String PROP_NAME_AUTO_PIT = "mpi-client.pid.updateIdTypes";

	public static final String PROP_NAME_MSH_8 = "mpi-client.security.authtoken";
	public static final String PROP_NAME_JKSTRUST_STORE = "mpi-client.security.trustStore";
	public static final String PROP_NAME_JKSTRUST_PASS = "mpi-client.security.trustStorePassword";
	public static final String PROP_NAME_JKSKEY_STORE = "mpi-client.security.keyStore";
	public static final String PROP_NAME_JKSKEY_PASS = "mpi-client.security.keyStorePassword";
	
	public static final String PROP_NAME_AR_ENDPOINT = "mpi-client.endpoint.ar.addr";
	public static final String PROP_NAME_AR_TRANSPORT = "mpi-client.endpoint.ar.transport";
	public static final String PROP_NAME_AR_PORT = "mpi-client.endpoint.ar.port";
	
	public static final String PROP_NAME_SND_NAME = "mpi-client.msg.sendingApplication";
	public static final String PROP_NAME_SND_FAC = "mpi-client.msg.sendingFacility";
	public static final String PROP_NAME_RCV_NAME = "mpi-client.msg.remoteApplication";
	public static final String PROP_NAME_RCV_FAC = "mpi-client.msg.remoteFacility";

	public static final String PROP_NAME_EXTMAP = "mpi-client.ext.extendedAttributes";
	public static final String PROP_NAME_USE_OMRS_RELS = "mpi-client.ext.storeNK1AsRelationships";
	
    private Map<String, Object> m_cachedProperties = new HashMap<String, Object>();

	
	/**
     * Read a global property
     */
    private <T> T getOrCreateGlobalProperty(String propertyName, T defaultValue)
    {
		Object retVal = this.m_cachedProperties.get(propertyName);
		
		if(retVal != null)
			return (T)retVal;
		else 
		{
			String propertyValue = Context.getAdministrationService().getGlobalProperty(propertyName);
			if(propertyValue != null && !propertyValue.isEmpty())
			{
				T value = (T)FormatterUtil.fromWireFormat(propertyValue, defaultValue.getClass()); 
				synchronized (s_lockObject) {
					this.m_cachedProperties.put(propertyName, value);
                }
				return value;
			}
			else
			{
				Context.getAdministrationService().saveGlobalProperty(new GlobalProperty(propertyName, defaultValue.toString()));
				synchronized (s_lockObject) {
					this.m_cachedProperties.put(propertyName, defaultValue);
                }
				return defaultValue;
			}
		}
    }

	

	/**
	 * HIE configuration utility
	 */
	MpiClientConfiguration() {
		
	}
	
	/**
	 * Get the instance of the configuration utility
	 * @return
	 */
	public static MpiClientConfiguration getInstance()
	{
		if(s_instance == null)
			synchronized (s_lockObject) {
				if(s_instance == null)
					s_instance = new MpiClientConfiguration();
			}
		return s_instance;
	}
	
	
	/**
	 * Get the enterprise patient identifier root
	 * @return
	 */
	public String getEnterprisePatientIdRoot() {
		return this.getOrCreateGlobalProperty(PROP_NAME_ENT_ID, "ENTID");
	}
	
	/**
	 * Get the enterprise patient identifier root
	 * @return
	 */
	public String getLocalPatientIdRoot() {
		return this.getOrCreateGlobalProperty(PROP_NAME_LOCAL_ID, "LOCAL");
	}

	/**
	 * Get the enterprise patient identifier root
	 * @return
	 */
	public String getPreferredPatientIdRoot() {
		return this.getOrCreateGlobalProperty(PROP_NAME_PREF_ID, "NAT_HEALTH_ID");
	}

    /**
     * Get the PDQ endpoint
     * @return
     */
    public String getPdqEndpoint() {
    	return this.getOrCreateGlobalProperty(PROP_NAME_PDQ_EP, "127.0.0.1");
    }
    
    /**
     * Get the PDQ port
     * @return
     */
    public Integer getPdqPort() {
    	return this.getOrCreateGlobalProperty(PROP_NAME_PDQ_EP_PORT, 2100);
    }
    
    /**
     * Get the PIX Endpoint
     * @return
     */
    public String getPixEndpoint() {
    	return this.getOrCreateGlobalProperty(PROP_NAME_PIX_EP, "127.0.0.1");
    }
    
    /**
     * Get the PIX POrt
     * @return
     */
    public Integer getPixPort() {
    	return this.getOrCreateGlobalProperty(PROP_NAME_PIX_EP_PORT, 2100);
    }
    
    /**
     * Get the XDS Registry endpoint
     * @return
     */
    public String getMsh8Security() {
    	return this.getOrCreateGlobalProperty(PROP_NAME_MSH_8, "");
    }
    
    /**
     * Get the audit repository endpoint
     * @return
     */
    public String getAuditRepositoryEndpoint() { return this.getOrCreateGlobalProperty(PROP_NAME_AR_ENDPOINT, "127.0.0.1"); }
    /**
     * Get the audit repository trasnport
     * @return
     */
    public String getAuditRepositoryTransport() { return this.getOrCreateGlobalProperty(PROP_NAME_AR_TRANSPORT, "audit-udp"); }
    /**
     * Get the audit repository port
     * @return
     */
    public int getAuditRepositoryPort() { return this.getOrCreateGlobalProperty(PROP_NAME_AR_PORT, 514); }
    /**
     * Get the key store file name
     * @return
     */
    public String getKeyStoreFile() { return this.getOrCreateGlobalProperty(PROP_NAME_JKSKEY_STORE, ""); }
    /**
     * Get the key store password
     * @return
     */
    public String getKeyStorePassword() { return this.getOrCreateGlobalProperty(PROP_NAME_JKSKEY_PASS, ""); }
    /**
     * Get the trust store file name
     * @return
     */
    public String getTrustStoreFile() { return this.getOrCreateGlobalProperty(PROP_NAME_JKSTRUST_STORE, ""); }
    /**
     * Get the trust store password
     * @return
     */
    public String getTrustStorePassword() { return this.getOrCreateGlobalProperty(PROP_NAME_JKSTRUST_PASS, ""); }
    
    /**
     * Gets the device name
     * @return The device name
     */
    public String getLocalApplication() { return this.getOrCreateGlobalProperty(PROP_NAME_SND_NAME, "OpenMRS"); }
    
    /**
     * Gets the facility name
     * @return The facility name
     */
    public String getLocalFacility() { return this.getOrCreateGlobalProperty(PROP_NAME_SND_FAC, "FACILITY_ID"); }
    
    /**
     * Gets the device name
     * @return The device name
     */
    public String getRemoteApplication() { return this.getOrCreateGlobalProperty(PROP_NAME_RCV_NAME, "MPI"); }
    
    /**
     * Gets the facility name
     * @return The facility name
     */
    public String getRemoteFacility() { return this.getOrCreateGlobalProperty(PROP_NAME_RCV_FAC, "NATION_ID"); }
    
    /**
     * Gets the name of the patient attribute which is the mother's name (to go in the PID segment)
     */
    public HashMap<String, String> getExtensionMap() { 
    	String propertyData = this.getOrCreateGlobalProperty(PROP_NAME_EXTMAP, "");
    	HashMap<String, String> retVal = new HashMap<String, String>();
    	if(!propertyData.isEmpty()) {
    		for(String kv : propertyData.split(","))
    		{
    			String[] key = kv.split(":");
    			retVal.put(key[0], key[1]);
    		}
    	}
    	return retVal;
	}    
    /**
     * Gets the fathers name patient attribute to be sent in a NK1 segment
     */
    public boolean getUseOpenMRSRelationships() { return this.getOrCreateGlobalProperty(PROP_NAME_USE_OMRS_RELS, false); }
    
    /**
     * Gets the auto update 
     * @return
     */
    public boolean getAutoUpdateLocalPatientIdentifierTypes() { return this.getOrCreateGlobalProperty(PROP_NAME_AUTO_PIT, false); }

}