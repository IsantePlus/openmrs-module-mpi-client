package org.openmrs.module.santedb.mpiclient.exception;

import ca.uhn.hl7v2.model.Message;

/**
 * Health information exchange communication base exception
 * @author Justin
 *
 */

public class MpiClientException extends Exception {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	// Backing field for message
	private Message m_message;
	
	/**
	 * HIE Exception
	 */
	public MpiClientException() {
		
	}
	
	/**
	 * Creates a new HIE exception
	 */
	public MpiClientException(Exception cause)
	{
		super(cause);
	}

	/**
	 * Create health information exception
	 * @param string
	 */
	public MpiClientException(String message) {
		super(message);
	}
	
	/**
	 * Create health information exception
	 * @param string
	 */
	public MpiClientException(String message, Message response) {
		super(message);
		this.m_message = response;
	}

	/**
	 * Create HIE Exception with cause
	 */
	public MpiClientException(String message, Exception e) {
		super(message, e);
	}
	
	/**
	 * Get the response message
	 */
	public Message getResponseMessage() {
		return this.m_message;
	}
}
