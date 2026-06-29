package com.mb.sfdcmcp.sfdcmcpsrv.config;

import com.mb.sfdcmcp.sfdcmcpsrv.service.SalesforceRestService;
import com.mb.sfdcmcp.sfdcmcpsrv.service.SalesforceUserImpersonationService;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;

@Configuration
@EnableConfigurationProperties(SalesforceProperties.class)
public class SalesforceConfig {

    @Bean
    public PrivateKey salesforcePrivateKey(SalesforceProperties props) throws Exception {
        String pem = new String(Files.readAllBytes(Paths.get(props.getPrivateKeyPath())))
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replaceAll("\\s", "")
                .trim();
        byte[] encoded = Base64.getDecoder().decode(pem);
        return KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(encoded));
    }

    @Bean
    public SalesforceUserImpersonationService salesforceImpersonationService(
            SalesforceProperties props, PrivateKey salesforcePrivateKey) {
        return new SalesforceUserImpersonationService(
                props.getClientId(), props.getOrgUrl(), salesforcePrivateKey);
    }

    @Bean
    public SalesforceRestService salesforceRestService(
            SalesforceProperties props,
            SalesforceUserImpersonationService salesforceImpersonationService) {
        return new SalesforceRestService(salesforceImpersonationService, props.getApiVersion());
    }
}