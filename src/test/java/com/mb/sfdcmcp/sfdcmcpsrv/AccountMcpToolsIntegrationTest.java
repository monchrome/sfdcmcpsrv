package com.mb.sfdcmcp.sfdcmcpsrv;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mb.sfdcmcp.sfdcmcpsrv.tool.SalesforceMcpTools;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for the describe_sobject, create_record, and soql_query MCP tools
 * against the Salesforce Account object.
 *
 * Requires salesforce.test-username to be set in application-test.properties.
 * The integration account impersonates that user via the JWT Bearer OAuth flow.
 * Tests are automatically skipped when the property is not configured.
 */
@SpringBootTest
@ActiveProfiles("test")
class AccountMcpToolsIntegrationTest {

    @Autowired
    SalesforceMcpTools mcpTools;

    @Value("${salesforce.test-username:}")
    String testUsername;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void requireTestUsername() {
        Assumptions.assumeTrue(
            testUsername != null && !testUsername.isBlank(),
            "salesforce.test-username not configured in src/test/resources/application-test.properties — skipping integration tests"
        );
    }

    @Test
    void describeAccount() throws Exception {
        String result = mcpTools.describeSobject(testUsername, "Account");

        JsonNode json = objectMapper.readTree(result);
        assertNull(json.get("error"), "Tool returned an error: " + result);
        assertEquals("Account", json.get("name").asText());
        assertTrue(json.get("queryable").asBoolean(), "Account should be queryable");
        assertTrue(json.get("createable").asBoolean(), "Account should be createable");
        assertTrue(json.get("updateable").asBoolean(), "Account should be updateable");
        assertTrue(json.get("fields").isArray(), "fields should be a JSON array");
        assertTrue(json.get("fields").size() > 0, "fields array should not be empty");

        // Verify the Name field (always present and createable on Account) is in the describe result
        boolean nameFieldFound = false;
        for (JsonNode field : json.get("fields")) {
            if ("Name".equals(field.get("name").asText())) {
                nameFieldFound = true;
                assertTrue(field.get("createable").asBoolean(), "Name field should be createable");
                break;
            }
        }
        assertTrue(nameFieldFound, "Name field should be present in Account describe result");
    }

    @Test
    void createAccount() throws Exception {
        String fieldsJson = "{\"Name\": \"MCP Integration Test Account\"}";
        String result = mcpTools.createRecord(testUsername, "Account", fieldsJson);

        JsonNode json = objectMapper.readTree(result);
        assertNull(json.get("error"), "Tool returned an error: " + result);
        assertTrue(json.get("success").asBoolean(), "create_record should report success=true");
        assertNotNull(json.get("id"), "Response should include an id field");
        assertFalse(json.get("id").asText().isBlank(), "id should not be blank");
        assertTrue(json.get("id").asText().startsWith("001"), "Account id should start with 001 key prefix");
        assertTrue(json.get("errors").isArray(), "errors should be a JSON array");
        assertEquals(0, json.get("errors").size(), "errors array should be empty on success");
    }

    @Test
    void createThenQueryAccount() throws Exception {
        // Create an Account with a name unique enough to locate in the query result
        String uniqueName = "MCP Test Account " + System.currentTimeMillis();
        String createFieldsJson = "{\"Name\": \"" + uniqueName + "\"}";

        String createResult = mcpTools.createRecord(testUsername, "Account", createFieldsJson);
        JsonNode createJson = objectMapper.readTree(createResult);
        assertNull(createJson.get("error"), "create_record failed: " + createResult);
        assertTrue(createJson.get("success").asBoolean());
        String createdId = createJson.get("id").asText();
        assertFalse(createdId.isBlank(), "Created record id must not be blank");

        // Query all Accounts and verify the newly created one appears in the results
        String queryResult = mcpTools.soqlQuery(testUsername, "Account", "[\"Id\", \"Name\"]");
        JsonNode queryJson = objectMapper.readTree(queryResult);
        assertNull(queryJson.get("error"), "soql_query failed: " + queryResult);
        assertTrue(queryJson.get("done").asBoolean(), "Query should be done=true");
        assertTrue(queryJson.get("totalSize").asInt() > 0, "Query should return at least one record");
        assertTrue(queryJson.get("records").isArray(), "records should be a JSON array");

        boolean found = false;
        for (JsonNode record : queryJson.get("records")) {
            if (createdId.equals(record.get("Id").asText())) {
                found = true;
                assertEquals(uniqueName, record.get("Name").asText(),
                    "Record Name should match what was created");
                break;
            }
        }
        assertTrue(found, "Newly created Account (id=" + createdId + ") was not found in soql_query results");
    }
}
