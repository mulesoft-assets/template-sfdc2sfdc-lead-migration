package org.mule.templates.integration;

import static junit.framework.Assert.assertEquals;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import junit.framework.Assert;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mule.MessageExchangePattern;
import org.mule.api.MuleEvent;
import org.mule.api.MuleException;
import org.mule.processor.chain.SubflowInterceptingChainLifecycleWrapper;

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
public class BusinessLogicTestDoNotCreateAccountIT extends AbstractTemplateTestCase {

	private List<Map<String, Object>> createdLeadsInA = new ArrayList<Map<String, Object>>();
	private List<Map<String, Object>> createdAccountsInB = new ArrayList<Map<String, Object>>();

	private BatchTestHelper helper;

	@BeforeClass
	public static void init() {
		System.setProperty("account.sync.policy", "");
		System.setProperty("account.id.in.b", "");
	}

	@AfterClass
	public static void shutDown() {
		System.clearProperty("account.sync.policy");
		System.clearProperty("account.id.in.b");
	}

	@Before
	public void setUp() throws Exception {

		helper = new BatchTestHelper(muleContext);

		// Flow to retrieve leads from target system after sync in g
		retrieveLeadFromBFlow = getSubFlow("retrieveLeadFromBFlow");
		retrieveLeadFromBFlow.initialise();

		retrieveAccountFlowFromB = getSubFlow("retrieveAccountFlowFromB");
		retrieveAccountFlowFromB.initialise();

		createTestDataInSandBox();
	}

	@After
	public void tearDown() throws Exception {
		deleteTestDataFromSandBox();
	}

	@Test
	public void testMainFlow() throws Exception {
		runFlow("mainFlow");

		// Wait for the batch job executed by the poll flow to finish
		helper.awaitJobTermination(TIMEOUT_SEC * 1000, 500);
		helper.assertJobWasSuccessful();

		Assert.assertEquals("The lead should not have been sync", null, invokeRetrieveFlow(retrieveLeadFromBFlow, createdLeadsInA.get(0)));

		Assert.assertEquals("The lead should not have been sync", null, invokeRetrieveFlow(retrieveLeadFromBFlow, createdLeadsInA.get(1)));

		Map<String, Object> payload = invokeRetrieveFlow(retrieveLeadFromBFlow, createdLeadsInA.get(2));
		Assert.assertEquals("The lead should have been sync", createdLeadsInA.get(2)
																					.get("Email"), payload.get("Email"));

		Map<String, Object> fourthLead = createdLeadsInA.get(3);
		payload = invokeRetrieveFlow(retrieveLeadFromBFlow, fourthLead);
		assertEquals("The lead should have been sync (Email)", fourthLead.get("Email"), payload.get("Email"));
		assertEquals("The lead should have been sync (FirstName)", fourthLead.get("FirstName"), payload.get("FirstName"));
	}

	@SuppressWarnings("unchecked")
	private void createTestDataInSandBox() throws MuleException, Exception {
		// Create object in target system to be updated
		Map<String, Object> lead_3_B = createLead("B", 3);
		lead_3_B.put("MailingCountry", "United States");
		List<Map<String, Object>> createdLeadInB = new ArrayList<Map<String, Object>>();
		createdLeadInB.add(lead_3_B);

		SubflowInterceptingChainLifecycleWrapper createLeadInBFlow = getSubFlow("createLeadFlowB");
		createLeadInBFlow.initialise();
		createLeadInBFlow.process(getTestEvent(createdLeadInB, MessageExchangePattern.REQUEST_RESPONSE));

		// Create leads in source system to be or not to be synced

		// This lead should not be sync
		Map<String, Object> lead_0_A = createLead("A", 0);
		lead_0_A.put("MailingCountry", "Argentina");

		// This lead should not be sync
		Map<String, Object> lead_1_A = createLead("A", 1);
		lead_1_A.put("MailingCountry", "Argentina");

		// This lead should BE sync
		Map<String, Object> lead_2_A = createLead("A", 2);

		// This lead should BE sync (updated)
		Map<String, Object> lead_3_A = createLead("A", 3);
		lead_3_A.put("Email", lead_3_B.get("Email"));

		createdLeadsInA.add(lead_0_A);
		createdLeadsInA.add(lead_1_A);
		createdLeadsInA.add(lead_2_A);
		createdLeadsInA.add(lead_3_A);

		SubflowInterceptingChainLifecycleWrapper createLeadInAFlow = getSubFlow("createLeadFlowA");
		createLeadInAFlow.initialise();

		MuleEvent event = createLeadInAFlow.process(getTestEvent(createdLeadsInA, MessageExchangePattern.REQUEST_RESPONSE));

		List<SaveResult> results = (List<SaveResult>) event.getMessage()
															.getPayload();
		System.out.println("Results from creation in A" + results.toString());
		for (int i = 0; i < results.size(); i++) {
			createdLeadsInA.get(i)
								.put("Id", results.get(i)
													.getId());
		}
		System.out.println("Results after adding" + createdLeadsInA.toString());
	}

	private void deleteTestDataFromSandBox() throws MuleException, Exception {
		deleteTestLeadFromSandBox(createdLeadsInA);
		deleteTestAccountFromSandBox(createdAccountsInB);
	}

}
