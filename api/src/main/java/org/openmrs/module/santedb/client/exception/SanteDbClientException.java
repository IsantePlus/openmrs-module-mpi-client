package org.openmrs.module.santedb.client.exception;


/**
 * Health information exchange communication base exception
 * @author Justin
 *
 */

public class SanteDbClientException extends Exception {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	/**
	 * HIE Exception
	 */
	public SanteDbClientException() {
		
	}
	
	/**
	 * Creates a new HIE exception
	 */
	public SanteDbClientException(Exception cause)
	{
		super(cause);
	}

	/**
	 * Create health information exception
	 * @param string
	 */
	public SanteDbClientException(String message) {
		super(message);
	}

	/**
	 * Create HIE Exception with cause
	 */
	public SanteDbClientException(String message, Exception e) {
		super(message, e);
	}
	
}
