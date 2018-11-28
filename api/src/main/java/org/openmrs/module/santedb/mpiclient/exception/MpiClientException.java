package org.openmrs.module.santedb.mpiclient.exception;


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
	 * Create HIE Exception with cause
	 */
	public MpiClientException(String message, Exception e) {
		super(message, e);
	}
	
}
