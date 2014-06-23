/**
 * Mule Anypoint Template
 * Copyright (c) MuleSoft, Inc.
 * All rights reserved.  http://www.mulesoft.com
 */

package org.mule.templates.util;

import java.util.HashMap;
import java.util.Map;

import junit.framework.Assert;

import org.junit.Test;
import org.mule.api.transformer.TransformerException;

/**
 * 
 * @author unknown
 * @author MartinZdila
 *
 */
public class LeadDateComparatorTest {
	@Test(expected = IllegalArgumentException.class)
	public void nullLeadA() {
		Map<String, String> contactA = null;

		Map<String, String> contactB = new HashMap<String, String>();
		contactB.put("Id", "I000032300ESE");
		contactB.put("LastModifiedDate", "2013-12-09T22:15:33.001Z");

		LeadDateComparator.isAfter(contactA, contactB);
	}

	@Test(expected = IllegalArgumentException.class)
	public void nullLeadB() {
		Map<String, String> contactA = new HashMap<String, String>();
		contactA.put("Id", "I000032300ESE");
		contactA.put("LastModifiedDate", "2013-12-09T22:15:33.001Z");

		Map<String, String> contactB = null;

		LeadDateComparator.isAfter(contactA, contactB);
	}

	@Test(expected = IllegalArgumentException.class)
	public void malFormedContactA() throws TransformerException {

		Map<String, String> contactA = new HashMap<String, String>();
		contactA.put("Id", "I0000323AE754F");

		Map<String, String> contactB = new HashMap<String, String>();
		contactB.put("Id", "I000032300ESE");
		contactB.put("LastModifiedDate", "2013-12-09T22:15:33.001Z");

		LeadDateComparator.isAfter(contactA, contactB);
	}

	@Test(expected = IllegalArgumentException.class)
	public void malFormedContactB() throws TransformerException {

		Map<String, String> contactA = new HashMap<String, String>();
		contactA.put("Id", "I0000323AE754F");
		contactA.put("LastModifiedDate", "2013-12-09T22:15:33.001Z");

		Map<String, String> contactB = new HashMap<String, String>();
		contactB.put("Id", "I000032300ESE");

		LeadDateComparator.isAfter(contactA, contactB);
	}

	@Test
	public void contactAIsAfterContactB() throws TransformerException {

		Map<String, String> contactA = new HashMap<String, String>();
		contactA.put("Id", "I0000323AE754F");
		contactA.put("LastModifiedDate", "2013-12-10T22:15:33.001Z");

		Map<String, String> contactB = new HashMap<String, String>();
		contactB.put("Id", "I000032300ESE");
		contactB.put("LastModifiedDate", "2013-12-09T22:15:33.001Z");

		Assert.assertTrue("The contact A should be after the contact B", LeadDateComparator.isAfter(contactA, contactB));
	}

	@Test
	public void contactAIsNotAfterContactB() throws TransformerException {

		Map<String, String> contactA = new HashMap<String, String>();
		contactA.put("Id", "I0000323AE754F");
		contactA.put("LastModifiedDate", "2013-12-08T22:15:33.001Z");

		Map<String, String> contactB = new HashMap<String, String>();
		contactB.put("Id", "I000032300ESE");
		contactB.put("LastModifiedDate", "2013-12-09T22:15:33.001Z");

		Assert.assertFalse("The contact A should not be after the contact B", LeadDateComparator.isAfter(contactA, contactB));
	}

	@Test
	public void contactAIsTheSameThatContactB() throws TransformerException {

		Map<String, String> contactA = new HashMap<String, String>();
		contactA.put("Id", "I0000323AE754F");
		contactA.put("LastModifiedDate", "2013-12-09T22:15:33.001Z");

		Map<String, String> contactB = new HashMap<String, String>();
		contactB.put("Id", "I000032300ESE");
		contactB.put("LastModifiedDate", "2013-12-09T22:15:33.001Z");

		Assert.assertFalse("The contact A should not be after the contact B", LeadDateComparator.isAfter(contactA, contactB));
	}

}
