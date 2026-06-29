# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Commands

```bash
# Build
./mvnw clean package

# Run all tests (smoke test only; integration tests are skipped unless test-username is set)
./mvnw test

# Run a single test class
./mvnw test -Dtest=SfdcmcpsrvApplicationTests

# Run integration tests (after setting salesforce.test-username in application-test.properties)
./mvnw test -Dspring.profiles.active=test

# Run integration tests for a specific entity
./mvnw test -Dtest=AccountMcpToolsIntegrationTest -Dspring.profiles.active=test
./mvnw test -Dtest=ContactMcpToolsIntegrationTest -Dspring.profiles.active=test

# Run the server (connects via stdio — not a web server)
./mvnw spring-boot:run
```

## Architecture

This is a **Spring Boot MCP (Model Context Protocol) server** that exposes Salesforce REST API capabilities to AI clients. It communicates over **stdin/stdout** — there is no HTTP port. Console logging is intentionally suppressed (`logging.pattern.console=`) so that log output doesn't corrupt the MCP stdio stream; logs go to `./logs/mcp-server.log` instead.

Stack: Java 25, Spring Boot 4.1.0, Spring AI 2.0.0 (`spring-ai-starter-mcp-server`).

### Salesforce Authentication

Authentication to Salesforce uses the **JWT Bearer OAuth 2.0 flow** (no user password required):

1. The app signs a short-lived JWT (3-minute expiry) with an RSA-256 private key (`server.key` in resources).
2. The JWT is posted to `{audience}/services/oauth2/token` to exchange for a Salesforce access token.
3. The response (`SalesforceTokenResponse`) carries `access_token` and `instance_url`.

`SalesforceUserImpersonationService` issues per-user tokens by setting a different `sub` in the JWT, enabling calls to execute as a specific Salesforce user (the "target user") rather than the integration user. It caches tokens per username with a 110-minute TTL and performs a single retry on 401.

### Key Configuration

`application.properties` drives Salesforce credentials and MCP metadata. Both service classes are plain Java (no `@Service` annotation) and are instantiated as Spring beans by `SalesforceConfig` (`@Configuration`):

- `salesforcePrivateKey` bean — reads the PEM file at `salesforce.private-key-path` and builds an RSA `PrivateKey`
- `salesforceImpersonationService` bean — wired with `clientId`, `orgUrl`, and the private key
- `salesforceRestService` bean — wired with the impersonation service and `apiVersion`

### MCP Tools

`SalesforceMcpTools` (`@Component`) exposes three tools via `@McpTool`:

| Tool name | Method | Key parameters |
|---|---|---|
| `describe_sobject` | `describeSobject` | `targetUsername`, `objectName` |
| `create_record` | `createRecord` | `targetUsername`, `objectName`, `fieldsJson` (JSON object) |
| `soql_query` | `soqlQuery` | `targetUsername`, `objectName`, `fieldsJson` (JSON array of field names) |

All tools accept a `targetUsername` — the Salesforce user the integration account impersonates for that call.

## Testing

### Smoke test

`SfdcmcpsrvApplicationTests` — a single `contextLoads()` test that verifies the Spring context starts and all beans (including Salesforce config) wire up correctly. Runs with the default profile; does not make live Salesforce API calls.

### Integration tests

Two test classes exercise the full MCP tool stack against a live Salesforce org:

- `AccountMcpToolsIntegrationTest` — describes Account schema, creates an Account, then creates-and-queries to verify the record appears in SOQL results.
- `ContactMcpToolsIntegrationTest` — describes Contact schema, creates a Contact, then creates-and-queries to verify the record appears in SOQL results.

**Setup before running:**

1. Open `src/test/resources/application-test.properties`.
2. Set `salesforce.test-username` to a Salesforce user that the integration account is authorised to impersonate.
3. Run with the `test` Spring profile active (see commands above).

Tests use `Assumptions.assumeTrue` to auto-skip when `salesforce.test-username` is blank, so they do not fail in CI environments where it is not configured.

> **Note:** Each test run creates real records in Salesforce. Periodically clean up records whose `Name` starts with `MCP` or `MCP Integration Test` in your test org.
