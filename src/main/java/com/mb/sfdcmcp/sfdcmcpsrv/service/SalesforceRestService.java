package com.mb.sfdcmcp.sfdcmcpsrv.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mb.sfdcmcp.sfdcmcpsrv.dto.CreateRecordResult;
import com.mb.sfdcmcp.sfdcmcpsrv.dto.DescribeSobjectResult;
import com.mb.sfdcmcp.sfdcmcpsrv.dto.FieldDescribe;
import com.mb.sfdcmcp.sfdcmcpsrv.dto.SalesforceTokenResponse;
import com.mb.sfdcmcp.sfdcmcpsrv.dto.SoqlQueryResult;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class SalesforceRestService {

    private final SalesforceUserImpersonationService impersonationService;
    private final String apiVersion;
    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final ObjectMapper objectMapper = new ObjectMapper();

    /** Thrown when Salesforce returns 401 so the public methods can trigger a token refresh and retry. */
    private static class UnauthorizedException extends RuntimeException {
        UnauthorizedException(String body) { super("Salesforce returned 401: " + body); }
    }

    public SalesforceRestService(SalesforceUserImpersonationService impersonationService, String apiVersion) {
        this.impersonationService = impersonationService;
        this.apiVersion = apiVersion;
    }

    // -------------------------------------------------------------------------
    // Public tool methods — each catches UnauthorizedException, invalidates the
    // cached token, and retries once with a fresh token.
    // -------------------------------------------------------------------------

    public DescribeSobjectResult describeSobject(String targetUsername, String objectName) throws Exception {
        try {
            return doDescribe(impersonationService.getAccessTokenForUser(targetUsername), objectName);
        } catch (UnauthorizedException e) {
            impersonationService.invalidateToken(targetUsername);
            return doDescribe(impersonationService.getAccessTokenForUser(targetUsername), objectName);
        }
    }

    public CreateRecordResult createRecord(String targetUsername, String objectName, String fieldsJson) throws Exception {
        try {
            return doCreate(impersonationService.getAccessTokenForUser(targetUsername), objectName, fieldsJson);
        } catch (UnauthorizedException e) {
            impersonationService.invalidateToken(targetUsername);
            return doCreate(impersonationService.getAccessTokenForUser(targetUsername), objectName, fieldsJson);
        }
    }

    public SoqlQueryResult soqlQuery(String targetUsername, String objectName, String fieldsJson) throws Exception {
        try {
            return doSoqlQuery(impersonationService.getAccessTokenForUser(targetUsername), objectName, fieldsJson);
        } catch (UnauthorizedException e) {
            impersonationService.invalidateToken(targetUsername);
            return doSoqlQuery(impersonationService.getAccessTokenForUser(targetUsername), objectName, fieldsJson);
        }
    }

    // -------------------------------------------------------------------------
    // Private implementation methods — accept an already-resolved token so the
    // same token is reused across multiple calls within one operation (e.g.
    // createRecord calls doDescribe internally without a second login).
    // -------------------------------------------------------------------------

    private DescribeSobjectResult doDescribe(SalesforceTokenResponse token, String objectName) throws Exception {
        String url = token.getInstanceUrl() + "/services/data/" + apiVersion
                + "/sobjects/" + objectName + "/describe/";
        HttpResponse<String> response = send(HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", "Bearer " + token.getAccessToken())
                .header("Accept", "application/json")
                .GET()
                .build());

        if (response.statusCode() != 200) {
            throw new RuntimeException("describe failed [" + response.statusCode() + "]: " + response.body());
        }

        JsonNode root = objectMapper.readTree(response.body());
        DescribeSobjectResult result = new DescribeSobjectResult();
        result.setName(root.get("name").asText());
        result.setLabel(root.get("label").asText());
        result.setCreateable(root.get("createable").asBoolean());
        result.setUpdateable(root.get("updateable").asBoolean());
        result.setQueryable(root.get("queryable").asBoolean());

        List<FieldDescribe> fields = new ArrayList<>();
        for (JsonNode f : root.get("fields")) {
            FieldDescribe fd = new FieldDescribe();
            fd.setName(f.get("name").asText());
            fd.setLabel(f.get("label").asText());
            fd.setType(f.get("type").asText());
            fd.setNillable(f.get("nillable").asBoolean());
            fd.setCreateable(f.get("createable").asBoolean());
            fd.setUpdateable(f.get("updateable").asBoolean());
            List<String> refs = new ArrayList<>();
            for (JsonNode ref : f.get("referenceTo")) {
                refs.add(ref.asText());
            }
            fd.setReferenceTo(refs);
            fields.add(fd);
        }
        result.setFields(fields);
        return result;
    }

    private CreateRecordResult doCreate(SalesforceTokenResponse token, String objectName, String fieldsJson) throws Exception {
        @SuppressWarnings("unchecked")
        Map<String, Object> fieldMap = objectMapper.readValue(fieldsJson, Map.class);

        // Validate provided fields are createable — reuses the same token, no extra login
        DescribeSobjectResult describe = doDescribe(token, objectName);
        Set<String> createableFields = new HashSet<>();
        for (FieldDescribe fd : describe.getFields()) {
            if (fd.isCreateable()) createableFields.add(fd.getName());
        }

        List<String> invalid = new ArrayList<>();
        for (String field : fieldMap.keySet()) {
            if (!createableFields.contains(field)) invalid.add(field);
        }
        if (!invalid.isEmpty()) {
            throw new IllegalArgumentException(
                    "Fields not createable on " + objectName + ": " + invalid
                    + ". Createable fields: " + createableFields);
        }

        String url = token.getInstanceUrl() + "/services/data/" + apiVersion + "/sobjects/" + objectName + "/";
        HttpResponse<String> response = send(HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", "Bearer " + token.getAccessToken())
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(fieldMap)))
                .build());

        if (response.statusCode() != 201) {
            throw new RuntimeException("create failed [" + response.statusCode() + "]: " + response.body());
        }

        JsonNode root = objectMapper.readTree(response.body());
        CreateRecordResult result = new CreateRecordResult();
        result.setId(root.get("id").asText());
        result.setSuccess(root.get("success").asBoolean());
        List<String> errors = new ArrayList<>();
        for (JsonNode err : root.get("errors")) errors.add(err.asText());
        result.setErrors(errors);
        return result;
    }

    private SoqlQueryResult doSoqlQuery(SalesforceTokenResponse token, String objectName, String fieldsJson) throws Exception {
        @SuppressWarnings("unchecked")
        List<String> fields = objectMapper.readValue(fieldsJson, List.class);

        String soql = "SELECT " + String.join(", ", fields) + " FROM " + objectName;
        String url = token.getInstanceUrl() + "/services/data/" + apiVersion + "/query?q="
                + URLEncoder.encode(soql, StandardCharsets.UTF_8);

        HttpResponse<String> response = send(HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", "Bearer " + token.getAccessToken())
                .header("Accept", "application/json")
                .GET()
                .build());

        if (response.statusCode() != 200) {
            throw new RuntimeException("SOQL query failed [" + response.statusCode() + "]: " + response.body());
        }

        JsonNode root = objectMapper.readTree(response.body());
        SoqlQueryResult result = new SoqlQueryResult();
        result.setTotalSize(root.get("totalSize").asInt());
        result.setDone(root.get("done").asBoolean());

        List<Map<String, Object>> records = new ArrayList<>();
        for (JsonNode record : root.get("records")) {
            @SuppressWarnings("unchecked")
            Map<String, Object> row = objectMapper.convertValue(record, Map.class);
            row.remove("attributes"); // strip Salesforce metadata envelope
            records.add(row);
        }
        result.setRecords(records);
        return result;
    }

    // -------------------------------------------------------------------------
    // Central HTTP send — the only place that detects 401 and signals expiry.
    // -------------------------------------------------------------------------

    private HttpResponse<String> send(HttpRequest request) throws Exception {
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() == 401) {
            throw new UnauthorizedException(response.body());
        }
        return response;
    }
}