package org.openmrs.module.santedb.mpiclient.util;

import java.util.Calendar;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.dcm4che3.audit.AuditMessage;
import org.dcm4che3.net.audit.AuditLogger;
import org.openmrs.module.santedb.mpiclient.api.impl.MpiClientServiceImpl;
import org.openmrs.module.santedb.mpiclient.exception.MpiClientException;

import ca.uhn.hl7v2.HL7Exception;
import ca.uhn.hl7v2.model.Message;
import ca.uhn.hl7v2.model.v231.datatype.ELD;
import ca.uhn.hl7v2.model.v231.message.ACK;
import ca.uhn.hl7v2.parser.PipeParser;
import ca.uhn.hl7v2.util.Terser;

/**
 * Represents a worker which dispatches an HL7 message
 * @author Justin Fyfe
 *
 */
public class MessageDispatchWorker extends Thread {

	// The message to be dispatch
	private Message m_message;
	private String m_endpoint;
	private Integer m_port;
	private AuditMessage m_auditMessage;
	
	// Log
	private Log m_log = LogFactory.getLog(MessageDispatchWorker.class);
		
	/**
	 * Creates a new dispatch worker
	 * @param msg The message to send
	 * @param auditMessage The audit message to send
	 * @param endpoint The endpoint to send the message to
	 * @param port The port to send the message to
	 */
	public MessageDispatchWorker(Message msg, AuditMessage auditMessage, String endpoint, Integer port) {
		this.m_message = msg;
		this.m_endpoint = endpoint;
		this.m_port = port;
		this.m_auditMessage = auditMessage;
	}
	
	/**
	 * Run the background process
	 */
	@Override
	public void run() {

		try
		{
		
			Message response = MessageUtil.getInstance().sendMessage(this.m_message, this.m_endpoint, this.m_port);
			
			Terser terser = new Terser(response);
			this.m_log.info(String.format("Message indicates: %s", terser.get("/MSA-1")));
			if(!terser.get("/MSA-1").endsWith("A"))
				throw new MpiClientException(String.format("Error from MPI :> %s", terser.get("/MSA-1")), response);
			
		}
		catch(MpiClientException e)
		{
			this.m_log.error("Error in PIX message", e);
			if(e.getResponseMessage() != null) {
				try {
					this.m_log.error(new PipeParser().encode(e.getResponseMessage()));
					ACK ack = ((ACK)e.getResponseMessage());
					for(ELD erd : ack.getERR().getErrorCodeAndLocation())
					{
						this.m_log.error(String.format("MPI Error: %s : %s", erd.getCodeIdentifyingError().getIdentifier().getValue(), erd.getCodeIdentifyingError().getText().getValue()));
					}
					e.printStackTrace();

				} catch (HL7Exception e1) {
					// TODO Auto-generated catch block
					e1.printStackTrace();
				}
			}
			
			this.m_auditMessage.getEventIdentification().setEventOutcomeIndicator("4");
		}
		catch(Exception e)
		{
			this.m_log.error(e);
			
			this.m_auditMessage.getEventIdentification().setEventOutcomeIndicator("4");
			e.printStackTrace();
		}
		finally
		{
			if(this.m_auditMessage != null)
				try
				{
					AuditLogger logger = AuditUtil.getInstance().createLoggerDevice().getDeviceExtension(AuditLogger.class);
					logger.write(Calendar.getInstance(), this.m_auditMessage);
				}
				catch(Exception e)
				{
					this.m_log.error(e);
				}
		}	
	}
}
