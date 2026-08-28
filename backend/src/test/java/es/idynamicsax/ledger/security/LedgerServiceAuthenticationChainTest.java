package es.idynamicsax.ledger.security;

import com.nimbusds.jose.jwk.*; import com.nimbusds.jose.jwk.source.*; import com.nimbusds.jose.proc.SecurityContext;
import es.idynamicsax.idax.config.ServiceTokenProperties; import es.idynamicsax.idax.security.*; import es.idynamicsax.idax.service.permission.PermissionService; import es.idynamicsax.idax.tenant.*;
import jakarta.servlet.FilterChain; import org.junit.jupiter.api.*; import org.springframework.mock.web.*; import org.springframework.security.core.context.SecurityContextHolder; import org.springframework.security.oauth2.jwt.*;
import java.security.KeyPairGenerator; import java.time.*; import java.util.*;
import static org.assertj.core.api.Assertions.*; import static org.mockito.Mockito.*;

class LedgerServiceAuthenticationChainTest {
 private JwtEncoder encoder; private LedgerJwtAuthFilter filter; private UUID tenant;
 @BeforeEach void setup() throws Exception {
  var gen=KeyPairGenerator.getInstance("RSA");gen.initialize(2048);var pair=gen.generateKeyPair();
  var rsa=new RSAKey.Builder((java.security.interfaces.RSAPublicKey)pair.getPublic()).privateKey((java.security.interfaces.RSAPrivateKey)pair.getPrivate()).build();
  encoder=new NimbusJwtEncoder(new ImmutableJWKSet<SecurityContext>(new JWKSet(rsa)));
  var decoder=NimbusJwtDecoder.withPublicKey((java.security.interfaces.RSAPublicKey)pair.getPublic()).build();
  var props=new ServiceTokenProperties("idax-service","idax-ledger",Duration.ofMinutes(5),Duration.ofMinutes(15),null,null,false,true);
  filter=new LedgerJwtAuthFilter(new ServiceTokenValidator(decoder,props),mock(TenantResolver.class));tenant=UUID.randomUUID();
 }
 @AfterEach void clear(){SecurityContextHolder.clearContext();TenantContext.clear();}
 @Test void authenticatesServiceAndPreservesLeastPrivilege() throws Exception {
  var request=new MockHttpServletRequest("POST","/api/ledger/proofs");request.addHeader("Authorization","Bearer "+token(Set.of("LEDGER_PROOF_CREATE"),"idax-ledger"));
  filter.doFilter(request,new MockHttpServletResponse(),mock(FilterChain.class));
  var principal=(CurrentUser)SecurityContextHolder.getContext().getAuthentication().getPrincipal();
  var permissions=new PermissionService(mock(org.springframework.jdbc.core.JdbcTemplate.class),mock(es.idynamicsax.idax.repository.IdaxPermissionRepository.class));
  assertThat(principal.isService()).isTrue();assertThat(principal.getTenantId()).isEqualTo(tenant);
  assertThat(permissions.hasPermission(principal,"LEDGER_PROOF_CREATE")).isTrue();assertThat(permissions.hasPermission(principal,"LEDGER_PROOF_VERIFY")).isFalse();
 }
 @Test void rejectsWrongAudienceAndTenantOverride() throws Exception {
  var wrong=new MockHttpServletRequest("POST","/api/ledger/proofs");wrong.addHeader("Authorization","Bearer "+token(Set.of("LEDGER_PROOF_CREATE"),"another-module"));
  filter.doFilter(wrong,new MockHttpServletResponse(),mock(FilterChain.class));assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
  var override=new MockHttpServletRequest("POST","/api/ledger/proofs");override.addHeader("Authorization","Bearer "+token(Set.of("LEDGER_PROOF_CREATE"),"idax-ledger"));override.addHeader("X-Tenant",UUID.randomUUID().toString());
  var response=new MockHttpServletResponse();filter.doFilter(override,response,mock(FilterChain.class));assertThat(response.getStatus()).isEqualTo(400);
 }
 private String token(Set<String> permissions,String audience){Instant now=Instant.now();var claims=JwtClaimsSet.builder().issuer("idax-service").subject(UUID.randomUUID().toString()).audience(List.of(audience)).issuedAt(now).expiresAt(now.plusSeconds(300)).id(UUID.randomUUID().toString()).claim("principal_type","SERVICE").claim("client_id","worker").claim("tenant_id",tenant.toString()).claim("permissions",permissions.stream().toList()).claim("roles",permissions.stream().toList()).build();return encoder.encode(JwtEncoderParameters.from(claims)).getTokenValue();}
}
