package com.mb.sfdcmcp.sfdcmcpsrv.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "salesforce")
public class SalesforceProperties {

    private String clientId;
    private String integrationUsername;
    private String orgUrl;
    private String privateKeyPath;
    private String apiVersion = "v63.0";

    public String getClientId() { return clientId; }
    public void setClientId(String clientId) { this.clientId = clientId; }

    public String getIntegrationUsername() { return integrationUsername; }
    public void setIntegrationUsername(String integrationUsername) { this.integrationUsername = integrationUsername; }

    public String getOrgUrl() { return orgUrl; }
    public void setOrgUrl(String orgUrl) { this.orgUrl = orgUrl; }

    public String getPrivateKeyPath() { return privateKeyPath; }
    public void setPrivateKeyPath(String privateKeyPath) { this.privateKeyPath = privateKeyPath; }

    public String getApiVersion() { return apiVersion; }
    public void setApiVersion(String apiVersion) { this.apiVersion = apiVersion; }
}