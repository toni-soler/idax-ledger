package es.idynamicsax.ledger.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtDecoders;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;

import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

@Configuration
public class LedgerJwtDecoderConfig {

    @Bean(name = "localJwtDecoder")
    @ConditionalOnExpression("'${idax.auth.mode:LOCAL}' == 'LOCAL' || '${idax.auth.mode:LOCAL}' == 'DUAL'")
    JwtDecoder localJwtDecoder(
            @Value("${idax.ledger.auth.public-key-location:classpath:keys/public.pem}") Resource resource)
            throws Exception {
        String pem;
        try (var input = resource.getInputStream()) {
            pem = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
        String encoded = pem
                .replace("-----BEGIN PUBLIC KEY-----", "")
                .replace("-----END PUBLIC KEY-----", "")
                .replaceAll("\\s", "");
        var specification = new X509EncodedKeySpec(Base64.getDecoder().decode(encoded));
        var publicKey = (RSAPublicKey) KeyFactory.getInstance("RSA").generatePublic(specification);
        return NimbusJwtDecoder.withPublicKey(publicKey).build();
    }

    @Bean(name = "keycloakJwtDecoder")
    @ConditionalOnExpression("'${idax.auth.mode:LOCAL}' == 'KEYCLOAK' || '${idax.auth.mode:LOCAL}' == 'DUAL'")
    JwtDecoder keycloakJwtDecoder(
            @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}") String issuerUri) {
        return JwtDecoders.fromIssuerLocation(issuerUri);
    }

    @Bean(name = "serviceJwtDecoder")
    JwtDecoder serviceJwtDecoder(
            @Value("${idax.service-auth.public-key-location}") Resource resource)
            throws Exception {
        String pem;
        try (var input = resource.getInputStream()) { pem = new String(input.readAllBytes(), StandardCharsets.UTF_8); }
        String encoded = pem.replace("-----BEGIN PUBLIC KEY-----", "").replace("-----END PUBLIC KEY-----", "").replaceAll("\\s", "");
        var key = (RSAPublicKey) KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(encoded)));
        return NimbusJwtDecoder.withPublicKey(key).signatureAlgorithm(SignatureAlgorithm.RS256).build();
    }
}
