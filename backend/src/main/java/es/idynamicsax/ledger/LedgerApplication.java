package es.idynamicsax.ledger;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import es.idynamicsax.ledger.config.LedgerProperties;
import es.idynamicsax.idax.config.ServiceTokenProperties;
import es.idynamicsax.idax.config.TokenValidatorConfig;
import es.idynamicsax.idax.config.TransactionConfig;
import es.idynamicsax.idax.security.DualTokenValidator;
import es.idynamicsax.idax.security.KeycloakTokenValidator;
import es.idynamicsax.idax.security.LocalTokenValidator;
import es.idynamicsax.idax.security.ServiceTokenValidator;
import es.idynamicsax.idax.service.audit.AuditUserContextResolver;
import es.idynamicsax.idax.service.audit.IdaxAuditEventWriter;
import es.idynamicsax.idax.service.audit.IdaxAuditService;
import es.idynamicsax.idax.tenant.AppUserResolver;
import es.idynamicsax.idax.tenant.DbSessionContextService;
import es.idynamicsax.idax.tenant.RlsTransactionAspect;
import es.idynamicsax.idax.tenant.TenantResolver;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EntityScan(basePackages = {"es.idynamicsax.ledger", "es.idynamicsax.idax.domain"})
@EnableJpaRepositories(basePackages = {"es.idynamicsax.ledger", "es.idynamicsax.idax.repository"})
@Import({
        TokenValidatorConfig.class,
        TransactionConfig.class,
        LocalTokenValidator.class,
        KeycloakTokenValidator.class,
        DualTokenValidator.class,
        ServiceTokenValidator.class,
        TenantResolver.class,
        AppUserResolver.class,
        DbSessionContextService.class,
        RlsTransactionAspect.class,
        IdaxAuditService.class,
        IdaxAuditEventWriter.class,
        AuditUserContextResolver.class
})
@EnableConfigurationProperties({LedgerProperties.class, ServiceTokenProperties.class})
@EnableMethodSecurity
@EnableScheduling
public class LedgerApplication {
    public static void main(String[] args) { SpringApplication.run(LedgerApplication.class, args); }
}
