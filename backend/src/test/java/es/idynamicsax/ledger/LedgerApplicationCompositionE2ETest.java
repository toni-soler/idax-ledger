package es.idynamicsax.ledger;

import es.idynamicsax.idax.security.mfa.MfaChallengeTokenService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.security.oauth2.jwt.JwtEncoder;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@EnabledIfEnvironmentVariable(named = "IDAX_LEDGER_RUN_STARTUP_E2E", matches = "true")
class LedgerApplicationCompositionE2ETest {
    @Autowired ApplicationContext context;

    @Test
    void actualLedgerApplicationStartsAsResourceServerWithoutPlatformAuthenticationServices() {
        assertThat(context.getBeansOfType(MfaChallengeTokenService.class)).isEmpty();
        assertThat(context.containsBean("localJwtEncoder")).isFalse();
        assertThat(context.getBeansOfType(JwtEncoder.class)).isEmpty();
        assertThat(context.containsBean("tokenValidator")).isTrue();
        assertThat(context.containsBean("ledgerSecurityFilterChain")).isTrue();
    }
}
