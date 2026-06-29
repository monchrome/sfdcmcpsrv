package com.mb.sfdcmcp.sfdcmcpsrv.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mb.sfdcmcp.sfdcmcpsrv.service.SalesforceRestService;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

@Component
public class SalesforceMcpTools {

    private final SalesforceRestService restService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public SalesforceMcpTools(SalesforceRestService restService) {
        this.restService = restService;
    }

    @McpTool(
        name = "describe_sobject",
        description = "Returns the schema of a Salesforce SObject (e.g. Account, Contact), including all field names, types, and which fields are createable or updateable."
    )
    public String describeSobject(
            @McpToolParam(description = "Salesforce username to act on behalf of (user impersonation)", required = true)
            String targetUsername,
            @McpToolParam(description = "Salesforce SObject API name, e.g. Account or Contact", required = true)
            String objectName) {
        try {
            return objectMapper.writeValueAsString(restService.describeSobject(targetUsername, objectName));
        } catch (Exception e) {
            return errorJson(e);
        }
    }

    @McpTool(
        name = "create_record",
        description = "Creates a new Salesforce record (Account or Contact). Validates that every field in fieldsJson is createable before submitting. Returns the new record ID."
    )
    public String createRecord(
            @McpToolParam(description = "Salesforce username to act on behalf of (user impersonation)", required = true)
            String targetUsername,
            @McpToolParam(description = "Salesforce SObject API name, e.g. Account or Contact", required = true)
            String objectName,
            @McpToolParam(description = "JSON object of field name/value pairs to set on the new record, e.g. {\"Name\":\"Acme\",\"Industry\":\"Technology\"}", required = true)
            String fieldsJson) {
        try {
            return objectMapper.writeValueAsString(restService.createRecord(targetUsername, objectName, fieldsJson));
        } catch (Exception e) {
            return errorJson(e);
        }
    }

    @McpTool(
        name = "soql_query",
        description = "Runs a SOQL SELECT query against a Salesforce object and returns matching records. Accepts a JSON array of field names to retrieve."
    )
    public String soqlQuery(
            @McpToolParam(description = "Salesforce username to act on behalf of (user impersonation)", required = true)
            String targetUsername,
            @McpToolParam(description = "Salesforce SObject API name to query, e.g. Account or Contact", required = true)
            String objectName,
            @McpToolParam(description = "JSON array of field names to include in SELECT, e.g. [\"Id\",\"Name\",\"Industry\"]", required = true)
            String fieldsJson) {
        try {
            return objectMapper.writeValueAsString(restService.soqlQuery(targetUsername, objectName, fieldsJson));
        } catch (Exception e) {
            return errorJson(e);
        }
    }

    private String errorJson(Exception e) {
        String msg = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
        return "{\"error\":" + objectMapper.createObjectNode().textNode(msg) + "}";
    }
}