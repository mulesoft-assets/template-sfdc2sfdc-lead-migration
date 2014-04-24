package org.mule.templates.integration;

import java.io.File;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.junit.Rule;
import org.mule.MessageExchangePattern;
import org.mule.api.MuleEvent;
import org.mule.api.MuleException;
import org.mule.api.config.MuleProperties;
import org.mule.api.lifecycle.InitialisationException;
import org.mule.processor.chain.SubflowInterceptingChainLifecycleWrapper;
import org.mule.tck.junit4.FunctionalTestCase;
import org.mule.tck.junit4.rule.DynamicPort;
import org.mule.templates.builders.SfdcObjectBuilder;
import org.mule.transport.NullPayload;

/**
 * This is the base test class for Template integration tests.
 * 
 * @author damiansima
 * @author MartinZdila
 */
public class AbstractTemplateTestCase extends FunctionalTestCase {

	private static final String MAPPINGS_FOLDER_PATH = "./mappings";
	private static final String TEST_FLOWS_FOLDER_PATH = "./src/test/resources/flows/";
	private static final String MULE_DEPLOY_PROPERTIES_PATH = "./src/main/app/mule-deploy.properties";

	protected static final int TIMEOUT_SEC = 120;
	protected static final String TEMPLATE_NAME = "lead-migration";

	protected SubflowInterceptingChainLifecycleWrapper retrieveLeadFromBFlow;
//	protected SubflowInterceptingChainLifecycleWrapper retrieveAccountFlowFromB;

	@Rule
	public DynamicPort port = new DynamicPort("http.port");

	@Override
	protected String getConfigResources() {
		String resources = "";
		try {
			Properties props = new Properties();
			props.load(new FileInputStream(MULE_DEPLOY_PROPERTIES_PATH));
			resources = props.getProperty("config.resources");
		} catch (Exception e) {
			throw new IllegalStateException(
					"Could not find mule-deploy.properties file on classpath. Please add any of those files or override the getConfigResources() method to provide the resources by your own.");
		}

		return resources + getTestFlows();
	}

	protected String getTestFlows() {
		StringBuilder resources = new StringBuilder();

		File testFlowsFolder = new File(TEST_FLOWS_FOLDER_PATH);
		File[] listOfFiles = testFlowsFolder.listFiles();
		if (listOfFiles != null) {
			for (File f : listOfFiles) {
				if (f.isFile() && f.getName()
									.endsWith("xml")) {
					resources.append(",")
								.append(TEST_FLOWS_FOLDER_PATH)
								.append(f.getName());
				}
			}
			return resources.toString();
		} else {
			return "";
		}
	}

	@Override
	protected Properties getStartUpProperties() {
		Properties properties = new Properties(super.getStartUpProperties());

		String pathToResource = MAPPINGS_FOLDER_PATH;
		File graphFile = new File(pathToResource);

		properties.put(MuleProperties.APP_HOME_DIRECTORY_PROPERTY, graphFile.getAbsolutePath());

		return properties;
	}

	@SuppressWarnings("unchecked")
	protected Map<String, Object> invokeRetrieveFlow(SubflowInterceptingChainLifecycleWrapper flow, Map<String, Object> payload) throws Exception {
		MuleEvent event = flow.process(getTestEvent(payload, MessageExchangePattern.REQUEST_RESPONSE));

		Object resultPayload = event.getMessage().getPayload();
		return resultPayload instanceof NullPayload ? null : (Map<String, Object>) resultPayload;
	}

	protected Map<String, Object> createLead(String orgId, int sequence) {
		return SfdcObjectBuilder.aLead()
								.with("FirstName", "FirstName_" + sequence)
								.with("LastName", buildUniqueName(TEMPLATE_NAME, "LastName_" + sequence + "_"))
								.with("Email", buildUniqueEmail("some.email." + sequence))
								.with("Description", "Some fake description")
								.with("Company", "ACME")
								.build();
	}

	protected void deleteTestLeadFromSandBox(List<Map<String, Object>> createdLeadsInA) throws Exception {
		deleteTestLeadsFromSandBoxA(createdLeadsInA);
		deleteTestLeadsFromSandBoxB(createdLeadsInA);
	}

	protected void deleteTestLeadsFromSandBoxA(List<Map<String, Object>> createdLeadsInA) throws InitialisationException, MuleException, Exception {
		SubflowInterceptingChainLifecycleWrapper deleteLeadFromAFlow = getSubFlow("deleteLeadFromAFlow");
		deleteLeadFromAFlow.initialise();
		deleteTestEntityFromSandBox(deleteLeadFromAFlow, createdLeadsInA);
	}

	protected void deleteTestLeadsFromSandBoxB(List<Map<String, Object>> createdLeadsInA) throws InitialisationException, MuleException, Exception {
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
	
	protected void deleteTestEntityFromSandBox(SubflowInterceptingChainLifecycleWrapper deleteFlow, List<Map<String, Object>> entitities) throws MuleException, Exception {
		List<String> idList = new ArrayList<String>();
		for (Map<String, Object> c : entitities) {
			System.out.println("CHOBOT "+c);
			idList.add(c.get("Id").toString());
		}
		deleteFlow.process(getTestEvent(idList, MessageExchangePattern.REQUEST_RESPONSE));
	}

	protected String buildUniqueName(String templateName, String name) {
		String timeStamp = new Long(new Date().getTime()).toString();

		StringBuilder builder = new StringBuilder();
		builder.append(name);
		builder.append(templateName);
		builder.append(timeStamp);

		return builder.toString();
	}

	protected String buildUniqueEmail(String user) {
		String server = "fakemail";

		StringBuilder builder = new StringBuilder();
		builder.append(buildUniqueName(TEMPLATE_NAME, user));
		builder.append("@");
		builder.append(server);
		builder.append(".com");

		return builder.toString();
	}
}
