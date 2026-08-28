package es.idynamicsax.ledger.security;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.proc.SecurityContext;
import es.idynamicsax.idax.config.ServiceTokenProperties;
import es.idynamicsax.idax.security.ServiceTokenValidator;
import es.idynamicsax.idax.security.CompositeTokenValidator;
import es.idynamicsax.idax.security.TokenValidator;
import es.idynamicsax.idax.service.permission.PermissionService;
import es.idynamicsax.idax.tenant.AppUserResolver;
import es.idynamicsax.idax.tenant.TenantResolver;
import es.idynamicsax.ledger.config.LedgerSecurityConfig;
import es.idynamicsax.ledger.controller.LedgerProofController;
import es.idynamicsax.ledger.proof.LedgerProofService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.test.web.servlet.MockMvc;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPublicKey;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes=LedgerServiceSecurityChainE2ETest.TestApplication.class)
@AutoConfigureMockMvc
class LedgerServiceSecurityChainE2ETest {
    @MockBean LedgerProofService ledgerProofService;
    @MockBean TenantResolver tenantResolver;
    @MockBean AppUserResolver appUserResolver;
    @MockBean(name="permissionService") PermissionService permissionService;
    @jakarta.annotation.Resource MockMvc mvc;
    @jakarta.annotation.Resource JwtEncoder serviceTestEncoder;

    private UUID tenant;

    @BeforeEach void setUp() {
        tenant=UUID.randomUUID();
        SecurityContextHolder.clearContext();
        org.mockito.Mockito.when(permissionService.hasPermission(org.mockito.ArgumentMatchers.anyString()))
                .thenAnswer(invocation->{
                    String permission=invocation.getArgument(0);
                    var authentication=SecurityContextHolder.getContext().getAuthentication();
                    return authentication!=null && authentication.getAuthorities().stream()
                            .anyMatch(authority->permission.equals(authority.getAuthority()));
                });
    }

    @Test void actualSecurityChainEnforcesCreateAndVerifyPermissions() throws Exception {
        String create=token(serviceTestEncoder,"idax-service","idax-ledger",tenant,
                Set.of("LEDGER_PROOF_CREATE"),Instant.now().plusSeconds(60));
        mvc.perform(post("/api/ledger/proofs").header("Authorization","Bearer "+create)
                .header("Idempotency-Key","k").contentType("application/json").content("{}"))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/ledger/proofs/"+UUID.randomUUID()+"/verify")
                .header("Authorization","Bearer "+create).contentType("application/json").content("{}"))
                .andExpect(status().isForbidden());
        String noCreate=token(serviceTestEncoder,"idax-service","idax-ledger",tenant,
                Set.of("LEDGER_READ"),Instant.now().plusSeconds(60));
        mvc.perform(post("/api/ledger/proofs").header("Authorization","Bearer "+noCreate)
                .header("Idempotency-Key","k2").contentType("application/json").content("{}"))
                .andExpect(status().isForbidden());
    }

    @Test void rejectsForeignTenantHeaderWrongAudienceWrongSignatureAndExpiry() throws Exception {
        mvc.perform(post("/api/ledger/proofs").header("Idempotency-Key","missing")
                .contentType("application/json").content("{}"))
                .andExpect(status().isUnauthorized());
        String valid=token(serviceTestEncoder,"idax-service","idax-ledger",tenant,
                Set.of("LEDGER_PROOF_CREATE"),Instant.now().plusSeconds(60));
        mvc.perform(post("/api/ledger/proofs").header("Authorization","Bearer "+valid)
                .header("X-Tenant",UUID.randomUUID()).header("Idempotency-Key","tenant-b")
                .contentType("application/json").content("{}"))
                .andExpect(status().isBadRequest());
        assertUnauthorized(token(serviceTestEncoder,"idax-service","another-module",tenant,
                Set.of("LEDGER_PROOF_CREATE"),Instant.now().plusSeconds(60)));
        assertUnauthorized(token(attackerEncoder(),"idax-service","idax-ledger",tenant,
                Set.of("LEDGER_PROOF_CREATE"),Instant.now().plusSeconds(60)));
        assertUnauthorized(token(serviceTestEncoder,"idax-service","idax-ledger",tenant,
                Set.of("LEDGER_PROOF_CREATE"),Instant.now().minusSeconds(1)));
        assertUnauthorized(token(serviceTestEncoder,"local-idax","idax-ledger",tenant,
                Set.of("LEDGER_PROOF_CREATE"),Instant.now().plusSeconds(60)));
        assertUnauthorized(unsignedToken());
        assertUnauthorized(hs256Token());
    }

    private void assertUnauthorized(String token) throws Exception {
        mvc.perform(post("/api/ledger/proofs").header("Authorization","Bearer "+token)
                .header("Idempotency-Key","bad").contentType("application/json").content("{}"))
                .andExpect(status().isUnauthorized());
    }

    private String token(JwtEncoder encoder,String issuer,String audience,UUID tenantId,
                         Set<String> permissions,Instant expiresAt) {
        Instant now=Instant.now();
        var claims=JwtClaimsSet.builder().issuer(issuer).subject(UUID.randomUUID().toString())
                .audience(List.of(audience)).issuedAt(now.minusSeconds(5)).expiresAt(expiresAt)
                .id(UUID.randomUUID().toString()).claim("principal_type","SERVICE")
                .claim("client_id","worker").claim("tenant_id",tenantId.toString())
                .claim("permissions",permissions.stream().sorted().toList()).build();
        return encoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(SignatureAlgorithm.RS256).build(),claims)).getTokenValue();
    }

    private JwtEncoder attackerEncoder() throws Exception {
        return encoder(generatePair());
    }

    private String unsignedToken() {
        Instant now=Instant.now();
        var claims=new com.nimbusds.jwt.JWTClaimsSet.Builder().issuer("idax-service")
                .subject(UUID.randomUUID().toString()).audience("idax-ledger")
                .issueTime(java.util.Date.from(now)).expirationTime(java.util.Date.from(now.plusSeconds(60)))
                .jwtID(UUID.randomUUID().toString()).claim("principal_type","SERVICE")
                .claim("client_id","worker").claim("tenant_id",tenant.toString())
                .claim("permissions",List.of("LEDGER_PROOF_CREATE")).build();
        return new com.nimbusds.jwt.PlainJWT(claims).serialize();
    }

    private String hs256Token() throws Exception {
        Instant now=Instant.now();
        var claims=new com.nimbusds.jwt.JWTClaimsSet.Builder().issuer("idax-service")
                .subject(UUID.randomUUID().toString()).audience("idax-ledger")
                .issueTime(java.util.Date.from(now)).expirationTime(java.util.Date.from(now.plusSeconds(60)))
                .jwtID(UUID.randomUUID().toString()).claim("principal_type","SERVICE")
                .claim("client_id","worker").claim("tenant_id",tenant.toString())
                .claim("permissions",List.of("LEDGER_PROOF_CREATE")).build();
        var jwt=new com.nimbusds.jwt.SignedJWT(
                new com.nimbusds.jose.JWSHeader(com.nimbusds.jose.JWSAlgorithm.HS256),claims);
        jwt.sign(new com.nimbusds.jose.crypto.MACSigner("01234567890123456789012345678901"));
        return jwt.serialize();
    }

    private static KeyPair generatePair() throws Exception {
        var generator=KeyPairGenerator.getInstance("RSA");generator.initialize(2048);return generator.generateKeyPair();
    }

    private static JwtEncoder encoder(KeyPair pair) {
        var rsa=new RSAKey.Builder((RSAPublicKey)pair.getPublic())
                .privateKey((java.security.interfaces.RSAPrivateKey)pair.getPrivate()).build();
        return new NimbusJwtEncoder(new ImmutableJWKSet<SecurityContext>(new JWKSet(rsa)));
    }

    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration(exclude=DataSourceAutoConfiguration.class)
    @Import({LedgerProofController.class, LedgerSecurityConfig.class, LedgerJwtAuthFilter.class,
            SecurityTestConfig.class})
    static class TestApplication {}

    @TestConfiguration
    @EnableMethodSecurity
    static class SecurityTestConfig {
        private static final KeyPair SERVICE_KEYS;
        static { try { SERVICE_KEYS=generatePair(); } catch(Exception e) { throw new ExceptionInInitializerError(e); } }

        @Bean JwtEncoder serviceTestEncoder(){ return encoder(SERVICE_KEYS); }
        @Bean(name="tokenValidator") TokenValidator tokenValidator(){
            var decoder=NimbusJwtDecoder.withPublicKey((RSAPublicKey)SERVICE_KEYS.getPublic())
                    .signatureAlgorithm(SignatureAlgorithm.RS256).build();
            var properties=new ServiceTokenProperties("idax-service","idax-ledger",
                    Duration.ofMinutes(5),Duration.ofMinutes(15),null,null,false,true);
            TokenValidator user=token->es.idynamicsax.idax.security.TokenValidationResult.invalid();
            return new CompositeTokenValidator(user,new ServiceTokenValidator(decoder,properties),properties);
        }
    }
}
