package com.mb.sfdcmcp.sfdcmcpsrv.service;

import com.mb.sfdcmcp.sfdcmcpsrv.dto.SalesforceTokenResponse;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.concurrent.ConcurrentHashMap;

import com.fasterxml.jackson.databind.ObjectMapper;

public class SalesforceUserImpersonationService {

    // Refresh 10 minutes before the 2-hour Salesforce token expiry
    private static final Duration TOKEN_TTL = Duration.ofMinutes(110);

    private final String clientId;
    private final String audience;
    private final java.security.PrivateKey privateKey;
    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final ConcurrentHashMap<String, CachedToken> tokenCache = new ConcurrentHashMap<>();

    private record CachedToken(SalesforceTokenResponse response, Instant expiresAt) {
        boolean isExpired() { return Instant.now().isAfter(expiresAt); }
    }

    public SalesforceUserImpersonationService(String clientId, String audience, java.security.PrivateKey privateKey) {
        this.clientId = clientId;
        this.audience = audience;
        this.privateKey = privateKey;
    }

    /**
     * Returns a cached token for the user, fetching a new one only when the cache is empty
     * or the cached token is within 10 minutes of expiry.
     */
    public SalesforceTokenResponse getAccessTokenForUser(String targetUsername) throws Exception {
        CachedToken cached = tokenCache.get(targetUsername);
        if (cached != null && !cached.isExpired()) {
            return cached.response();
        }
        return fetchAndCache(targetUsername);
    }

    /**
     * Removes the cached token for a user. Called by SalesforceRestService when a Salesforce
     * REST call returns 401, so the next getAccessTokenForUser will trigger a fresh exchange.
     */
    public void invalidateToken(String targetUsername) {
        tokenCache.remove(targetUsername);
    }

    private synchronized SalesforceTokenResponse fetchAndCache(String targetUsername) throws Exception {
        // Double-check: another thread may have refreshed while we waited for the lock
        CachedToken cached = tokenCache.get(targetUsername);
        if (cached != null && !cached.isExpired()) {
            return cached.response();
        }
        SalesforceTokenResponse response = doFetchToken(targetUsername);
        tokenCache.put(targetUsername, new CachedToken(response, Instant.now().plus(TOKEN_TTL)));
        return response;
    }

    private String generateJWTForUser(String targetUsername) {
        Instant now = Instant.now();
        return Jwts.builder()
                .setIssuer(clientId)
                .setSubject(targetUsername)
                .setAudience(audience)
                .setExpiration(Date.from(now.plusSeconds(180)))
                .setIssuedAt(Date.from(now))
                .signWith(privateKey, SignatureAlgorithm.RS256)
                .compact();
    }

    private SalesforceTokenResponse doFetchToken(String targetUsername) throws Exception {
        String jwt = generateJWTForUser(targetUsername);
        String requestBody = "grant_type=urn:ietf:params:oauth:grant-type:jwt-bearer&assertion=" + jwt;

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(audience + "/services/oauth2/token"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new RuntimeException("Failed user impersonation token exchange: " + response.body());
        }

        return objectMapper.readValue(response.body(), SalesforceTokenResponse.class);
    }
}
