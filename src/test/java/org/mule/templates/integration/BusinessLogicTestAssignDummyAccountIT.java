package org.mule.templates.integration;

import static junit.framework.Assert.assertEquals;
import static org.mule.templates.builders.SfdcObjectBuilder.anAccount;

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
 * The test validates that no account will get sync as result of the integration but the leads will belong to a particular predifined account.
 * 
 * 
 * @author damiansima
 * @author MartinZdila
 */
public class BusinessLogicTestAssignDummyAccountIT extends AbstractTemplateTestCase {
	private static final String ACCOUNT_ID_IN_B = "001n0000003gwUyAAI";
	private BatchTestHelper helper;

	private List<Map<String, Object>> createdLeadsInA = new ArrayList<Map<String, Object>>();
	private List<Map<String, Object>> createdAccountsInA = new ArrayList<Map<String, Object>>();

	@BeforeClass
	public static void init() {
		System.setProperty("account.sync.policy", "assignDummyAccount");
		System.setProperty("account.id.in.b", ACCOUNT_ID_IN_B);
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

		Map<String, Object> contacPayload = invokeRetrieveFlow(retrieveLeadFromBFlow, createdLeadsInA.get(2));
		Assert.assertEquals("The lead should have been sync", createdLeadsInA.get(2)
																					.get("Email"), contacPayload.get("Email"));
		Assert.assertEquals("The lead should belong to a different account ", ACCOUNT_ID_IN_B, contacPayload.get("AccountId"));

		Map<String, Object> accountPayload = invokeRetrieveFlow(retrieveAccountFlowFromB, createdAccountsInA.get(0));
		Assert.assertNull("The Account shouldn't have been sync.", accountPayload);

		Map<String, Object> fourthLead = createdLeadsInA.get(3);
		contacPayload = invokeRetrieveFlow(retrieveLeadFromBFlow, fourthLead);
		assertEquals("The lead should have been sync (Email)", fourthLead.get("Email"), contacPayload.get("Email"));
		assertEquals("The lead should have been sync (FirstName)", fourthLead.get("FirstName"), contacPayload.get("FirstName"));
	}

	private void createTestDataInSandBox() throws MuleException, Exception {
		createAccounts();
		createLeads();
	}

	@SuppressWarnings("unchecked")
	private void createAccounts() throws Exception {
		SubflowInterceptingChainLifecycleWrapper flow = getSubFlow("createAccountFlow");
		flow.initialise();
		createdAccountsInA.add(anAccount().with("Name", buildUniqueName(TEMPLATE_NAME, "ReferencedAccountTest"))
											.with("BillingCity", "San Francisco")
											.with("BillingCountry", "USA")
											.with("Phone", "123456789")
											.with("Industry", "Education")
											.with("NumberOfEmployees", 9000)
											.build());

		MuleEvent event = flow.process(getTestEvent(createdAccountsInA, MessageExchangePattern.REQUEST_RESPONSE));
		List<SaveResult> results = (List<SaveResult>) event.getMessage()
															.getPayload();
		for (int i = 0; i < results.size(); i++) {
			createdAccountsInA.get(i)
								.put("Id", results.get(i)
													.getId());
		}

		System.out.println("Results of data creation in sandbox" + createdAccountsInA.toString());
	}

	@SuppressWarnings("unchecked")
	private void createLeads() throws Exception {
		// Create object in target system to be updated
		Map<String, Object> lead_3_B = createLead("B", 3);
		lead_3_B.put("MailingCountry", "United States");
		List<Map<String, Object>> createdLeadInB = new ArrayList<Map<String, Object>>();
		createdLeadInB.add(lead_3_B);

		SubflowInterceptingChainLifecycleWrapper createLeadInBFlow = getSubFlow("createLeadFlowB");
		createLeadInBFlow.initialise();
		createLeadInBFlow.process(getTestEvent(createdLeadInB, MessageExchangePattern.REQUEST_RESPONSE));

		// This lead should not be sync
		Map<String, Object> lead_0_A = createLead("A", 0);
		lead_0_A.put("MailingCountry", "Argentina");

		// This lead should not be sync
		Map<String, Object> lead_1_A = createLead("A", 1);
		lead_1_A.put("MailingCountry", "Argentina");

		// This lead should BE sync with it's account
		Map<String, Object> lead_2_A = createLead("A", 2);
		lead_2_A.put("AccountId", createdAccountsInA.get(0)
														.get("Id"));

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
		deleteTestAccountFromSandBox(createdAccountsInA);
	}

}
