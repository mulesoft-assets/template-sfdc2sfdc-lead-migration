/**
 * Mule Anypoint Template
 * Copyright (c) MuleSoft, Inc.
 * All rights reserved.  http://www.mulesoft.com
 */

package org.mule.templates.integration;

import static junit.framework.Assert.assertEquals;

import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import junit.framework.Assert;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mule.MessageExchangePattern;
import org.mule.api.MuleEvent;
import org.mule.api.MuleException;
import org.mule.api.config.MuleProperties;
import org.mule.api.lifecycle.InitialisationException;
import org.mule.processor.chain.SubflowInterceptingChainLifecycleWrapper;
import org.mule.tck.junit4.FunctionalTestCase;
import org.mule.transport.NullPayload;

import com.mulesoft.module.batch.BatchTestHelper;
import com.sforce.soap.partner.SaveResult;

/**
 * The objective of this class is to validate the correct behavior of the Mule Template that make calls to external systems.
 * 
 * The test will invoke the batch process and afterwards check that the leads had been correctly created and that the ones that should be filtered are not in
 * the destination sand box.
 * 
 * The test validates that no account will get sync as result of the integration.
 * 
 * @author damiansima
 * @author MartinZdila
 */
@SuppressWarnings("deprecation")
public class BusinessLogicIT extends FunctionalTestCase {

	private final static Logger LOGGER = LogManager.getLogger(BusinessLogicIT.class);	
	private static final String MAPPINGS_FOLDER_PATH = "./mappings";
	private static final String TEST_FLOWS_FOLDER_PATH = "./src/test/resources/flows/";
	private static final String MULE_DEPLOY_PROPERTIES_PATH = "./src/main/app/mule-deploy.properties";

	protected static final int TIMEOUT_SEC = 480;
	protected static final String TEMPLATE_NAME = "lead-migration";

	protected SubflowInterceptingChainLifecycleWrapper retrieveLeadFromBFlow;
	private List<Map<String, Object>> createdLeadsInA = new ArrayList<Map<String, Object>>();
	private BatchTestHelper helper;

//	@Rule
//	public DynamicPort port = new DynamicPort("http.port");

	@Before
	public void setUp() throws Exception {
		helper = new BatchTestHelper(muleContext);
	
		// Flow to retrieve leads from target system after sync in g
		retrieveLeadFromBFlow = getSubFlow("retrieveLeadFromBFlow");
		retrieveLeadFromBFlow.initialise();
	
		createTestDataInSandBox();
	}

	@After
	public void tearDown() throws Exception {
		deleteTestLeadsFromSandBoxA(createdLeadsInA);
		deleteTestLeadsFromSandBoxB(createdLeadsInA);
	}

	@Test
	public void testMainFlow() throws Exception {
		runFlow("mainFlow");
	
		// Wait for the batch job executed by the poll flow to finish
		helper.awaitJobTermination(TIMEOUT_SEC * 1000, 500);
		helper.assertJobWasSuccessful();
	
		Map<String, Object> payload = invokeRetrieveFlow(retrieveLeadFromBFlow, createdLeadsInA.get(0));
		Assert.assertEquals("The lead should have been sync", createdLeadsInA.get(0).get("Email"), payload.get("Email"));
	
		Map<String, Object> fourthLead = createdLeadsInA.get(1);
		payload = invokeRetrieveFlow(retrieveLeadFromBFlow, fourthLead);
		assertEquals("The lead should have been sync (Email)", fourthLead.get("Email"), payload.get("Email"));
		assertEquals("The lead should have been sync (FirstName)", fourthLead.get("FirstName"), payload.get("FirstName"));
	}

	@Override
	protected String getConfigResources() {
		Properties props = new Properties();
		try {
			props.load(new FileInputStream(MULE_DEPLOY_PROPERTIES_PATH));
		} catch (IOException e) {
			throw new IllegalStateException(
					"Could not find mule-deploy.properties file on classpath. " +
					"Please add any of those files or override the getConfigResources() method to provide the resources by your own.");
		}

		return props.getProperty("config.resources") + getTestFlows();
	}


	@SuppressWarnings("unchecked")
	private void createTestDataInSandBox() throws MuleException, Exception {
		// Create object in target system to be updated
		Map<String, Object> lead_3_B = createLead("B", 3);
		List<Map<String, Object>> createdLeadInB = new ArrayList<Map<String, Object>>();
		createdLeadInB.add(lead_3_B);
	
		SubflowInterceptingChainLifecycleWrapper createLeadInBFlow = getSubFlow("createLeadFlowB");
		createLeadInBFlow.initialise();
		createLeadInBFlow.process(getTestEvent(createdLeadInB, MessageExchangePattern.REQUEST_RESPONSE));
	
		// Create leads in source system to be or not to be synced
	
		// This lead should BE sync
		Map<String, Object> lead_0_A = createLead("A", 0);
	
		// This lead should BE sync (updated)
		Map<String, Object> lead_1_A = createLead("A", 1);
		lead_1_A.put("Email", lead_3_B.get("Email"));
	
		createdLeadsInA.add(lead_0_A);
		createdLeadsInA.add(lead_1_A);
	
		SubflowInterceptingChainLifecycleWrapper createLeadInAFlow = getSubFlow("createLeadFlowA");
		createLeadInAFlow.initialise();
	
		MuleEvent event = createLeadInAFlow.process(getTestEvent(createdLeadsInA, MessageExchangePattern.REQUEST_RESPONSE));
	
		List<SaveResult> results = (List<SaveResult>) event.getMessage().getPayload();
		LOGGER.info("Results from creation in A" + results.toString());
		for (int i = 0; i < results.size(); i++) {
			createdLeadsInA.get(i).put("Id", results.get(i).getId());
		}
		System.out.println("Results after adding" + createdLeadsInA.toString());
	}

	private String getTestFlows() {
		File[] listOfFiles = new File(TEST_FLOWS_FOLDER_PATH).listFiles(new FileFilter() {
			@Override
			public boolean accept(File f) {
				return f.isFile() && f.getName().endsWith(".xml");
			}
		});
		
		if (listOfFiles == null) {
			return "";
		}
		
		StringBuilder resources = new StringBuilder();
		for (File f : listOfFiles) {
			resources.append(",").append(TEST_FLOWS_FOLDER_PATH).append(f.getName());
		}
		return resources.toString();
	}

	@Override
	protected Properties getStartUpProperties() {
		Properties properties = new Properties(super.getStartUpProperties());
		properties.put(
				MuleProperties.APP_HOME_DIRECTORY_PROPERTY,
				new File(MAPPINGS_FOLDER_PATH).getAbsolutePath());
		return properties;
	}

	@SuppressWarnings("unchecked")
	protected Map<String, Object> invokeRetrieveFlow(SubflowInterceptingChainLifecycleWrapper flow, Map<String, Object> payload) throws Exception {
		MuleEvent event = flow.process(getTestEvent(payload, MessageExchangePattern.REQUEST_RESPONSE));
		Object resultPayload = event.getMessage().getPayload();
		return resultPayload instanceof NullPayload ? null : (Map<String, Object>) resultPayload;
	}

	private Map<String, Object> createLead(String orgId, int sequence) {
		Map<String, Object> fields = new HashMap<String, Object>();
		fields.put("FirstName", "FirstName_" + sequence);
		fields.put("LastName", buildUniqueName(TEMPLATE_NAME, "LastName_" + sequence + "_"));
		fields.put("Email", buildUniqueName(TEMPLATE_NAME, "some.email." + sequence) + "@fakemail.com");
		fields.put("Description", "Some fake description");
		fields.put("Company", "ACME");
		return fields;
	}
	
	private String buildUniqueName(String templateName, String name) {
		return name + templateName + System.currentTimeMillis();
	}
	
	private void deleteTestLeadsFromSandBoxA(List<Map<String, Object>> createdLeadsInA) throws InitialisationException, MuleException, Exception {
		SubflowInterceptingChainLifecycleWrapper deleteLeadFromAFlow = getSubFlow("deleteLeadFromAFlow");
		deleteLeadFromAFlow.initialise();
		deleteTestEntityFromSandBox(deleteLeadFromAFlow, createdLeadsInA);
	}

	private void deleteTestLeadsFromSandBoxB(List<Map<String, Object>> createdLeadsInA) throws InitialisationException, MuleException, Exception {
		List<Map<String, Object>> createdLeadsInB = new ArrayList<Map<String, Object>>();
		for (Map<String, Object> c : createdLeadsInA) {
			Map<String, Object> lead = invokeRetrieveFlow(retrieveLeadFromBFlow, c);
			if (lead != null) {
				createdLeadsInB.add(lead);
			}
		}
		SubflowInterceptingChainLifecycleWrapper deleteLeadFromBFlow = getSubFlow("deleteLeadFromBFlow");
		deleteLeadFromBFlow.initialise();
		deleteTestEntityFromSandBox(deleteLeadFromBFlow, createdLeadsInB);
	}
	
	private void deleteTestEntityFromSandBox(SubflowInterceptingChainLifecycleWrapper deleteFlow, List<Map<String, Object>> entitities) throws MuleException, Exception {
		List<String> idList = new ArrayList<String>();
		for (Map<String, Object> c : entitities) {
			idList.add(c.get("Id").toString());
		}
		deleteFlow.process(getTestEvent(idList, MessageExchangePattern.REQUEST_RESPONSE));
	}

}
