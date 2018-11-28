package org.openmrs.module.santedb.mpiclient.model;

import java.util.ArrayList;
import java.util.List;

import org.openmrs.Patient;
import org.openmrs.Relationship;

/**
 * Represents temporary structure in memory to use for storing patient data
 * @author justin
 *
 */
public class MpiPatient extends Patient {

	
	// Backing field for relationships
	private List<Relationship> m_relationships = new ArrayList<Relationship>();
	
	/**
	 * Get relationships of the patient
	 * @return
	 */
	public List<Relationship> getRelationships() { return this.m_relationships; }
	
	/**
	 * Add a relationship to the patient
	 * @param relationship
	 */
	public void addRelationship(Relationship relationship) {
		this.m_relationships.add(relationship);
	}
	
}
