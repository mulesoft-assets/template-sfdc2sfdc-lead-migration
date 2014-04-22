package org.mule.templates.util;

import java.util.Map;

import org.apache.commons.lang.Validate;
import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;

/**
 * The function of this class is to establish a relation happens before between two maps representing SFDC leads.
 * 
 * It's assumed that these maps are well formed maps from SFDC thus they both contain an entry with the expected key. Never the less validations are being done.
 * 
 * @author damiansima
 * @author MartinZdila
 */
public class LeadDateComparator {
	private static final String LAST_MODIFIED_DATE = "LastModifiedDate";

	/**
	 * Validate which lead has the latest last modification date.
	 * 
	 * @param leadA
	 *            SFDC lead map
	 * @param leadB
	 *            SFDC lead map
	 * @return true if the last modified date from leadA is after the one from lead B
	 */
	public static boolean isAfter(Map<String, String> leadA, Map<String, String> leadB) {
		Validate.notNull(leadA, "The lead A should not be null");
		Validate.notNull(leadB, "The lead B should not be null");

		Validate.isTrue(leadA.containsKey(LAST_MODIFIED_DATE), "The lead A map should contain the key " + LAST_MODIFIED_DATE);
		Validate.isTrue(leadB.containsKey(LAST_MODIFIED_DATE), "The lead B map should contain the key " + LAST_MODIFIED_DATE);

		DateTimeFormatter formatter = DateTimeFormat.forPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
		DateTime lastModifiedDateOfA = formatter.parseDateTime(leadA.get(LAST_MODIFIED_DATE));
		DateTime lastModifiedDateOfB = formatter.parseDateTime(leadB.get(LAST_MODIFIED_DATE));

		return lastModifiedDateOfA.isAfter(lastModifiedDateOfB);
	}
}
