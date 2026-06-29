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
 * against the Salesforce Contact object.
 *
 * Requires salesforce.test-username to be set in application-test.properties.
 * The integration account impersonates that user via the JWT Bearer OAuth flow.
 * Tests are automatically skipped when the property is not configured.
 */
@SpringBootTest
@ActiveProfiles("test")
class ContactMcpToolsIntegrationTest {

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
    void describeContact() throws Exception {
        String result = mcpTools.describeSobject(testUsername, "Contact");

        JsonNode json = objectMapper.readTree(result);
        assertNull(json.get("error"), "Tool returned an error: " + result);
        assertEquals("Contact", json.get("name").asText());
        assertTrue(json.get("queryable").asBoolean(), "Contact should be queryable");
        assertTrue(json.get("createable").asBoolean(), "Contact should be createable");
        assertTrue(json.get("updateable").asBoolean(), "Contact should be updateable");
        assertTrue(json.get("fields").isArray(), "fields should be a JSON array");
        assertTrue(json.get("fields").size() > 0, "fields array should not be empty");

        // Verify LastName (required) and FirstName are present and createable
        boolean lastNameFound = false;
        boolean firstNameFound = false;
        for (JsonNode field : json.get("fields")) {
            String name = field.get("name").asText();
            if ("LastName".equals(name)) {
                lastNameFound = true;
                assertTrue(field.get("createable").asBoolean(), "LastName field should be createable");
            } else if ("FirstName".equals(name)) {
                firstNameFound = true;
                assertTrue(field.get("createable").asBoolean(), "FirstName field should be createable");
            }
        }
        assertTrue(lastNameFound, "LastName field should be present in Contact describe result");
        assertTrue(firstNameFound, "FirstName field should be present in Contact describe result");
    }

    @Test
    void createContact() throws Exception {
        String fieldsJson = "{\"LastName\": \"MCPIntegrationTest\", \"FirstName\": \"MCP\"}";
        String result = mcpTools.createRecord(testUsername, "Contact", fieldsJson);

        JsonNode json = objectMapper.readTree(result);
        assertNull(json.get("error"), "Tool returned an error: " + result);
        assertTrue(json.get("success").asBoolean(), "create_record should report success=true");
        assertNotNull(json.get("id"), "Response should include an id field");
        assertFalse(json.get("id").asText().isBlank(), "id should not be blank");
        assertTrue(json.get("id").asText().startsWith("003"), "Contact id should start with 003 key prefix");
        assertTrue(json.get("errors").isArray(), "errors should be a JSON array");
        assertEquals(0, json.get("errors").size(), "errors array should be empty on success");
    }

    @Test
    void createThenQueryContact() throws Exception {
        // Create a Contact with a unique LastName to locate in the query result
        String uniqueLastName = "MCPTest" + System.currentTimeMillis();
        String createFieldsJson = "{\"LastName\": \"" + uniqueLastName + "\", \"FirstName\": \"MCP\"}";

        String createResult = mcpTools.createRecord(testUsername, "Contact", createFieldsJson);
        JsonNode createJson = objectMapper.readTree(createResult);
        assertNull(createJson.get("error"), "create_record failed: " + createResult);
        assertTrue(createJson.get("success").asBoolean());
        String createdId = createJson.get("id").asText();
        assertFalse(createdId.isBlank(), "Created record id must not be blank");

        // Query all Contacts and verify the newly created one appears in the results
        String queryResult = mcpTools.soqlQuery(testUsername, "Contact", "[\"Id\", \"FirstName\", \"LastName\", \"Email\"]");
        JsonNode queryJson = objectMapper.readTree(queryResult);
        assertNull(queryJson.get("error"), "soql_query failed: " + queryResult);
        assertTrue(queryJson.get("done").asBoolean(), "Query should be done=true");
        assertTrue(queryJson.get("totalSize").asInt() > 0, "Query should return at least one record");
        assertTrue(queryJson.get("records").isArray(), "records should be a JSON array");

        boolean found = false;
        for (JsonNode record : queryJson.get("records")) {
            if (createdId.equals(record.get("Id").asText())) {
                found = true;
                assertEquals(uniqueLastName, record.get("LastName").asText(),
                    "Record LastName should match what was created");
                assertEquals("MCP", record.get("FirstName").asText(),
                    "Record FirstName should match what was created");
                break;
            }
        }
        assertTrue(found, "Newly created Contact (id=" + createdId + ") was not found in soql_query results");
    }
}
