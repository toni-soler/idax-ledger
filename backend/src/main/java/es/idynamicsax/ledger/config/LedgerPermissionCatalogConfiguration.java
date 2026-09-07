package es.idynamicsax.ledger.config;

import es.idynamicsax.idax.domain.IdaxPermission;
import es.idynamicsax.idax.repository.IdaxPermissionRepository;
import es.idynamicsax.idax.repository.IdaxAuditEventRepository;
import es.idynamicsax.idax.repository.TenantLegacyRepository;
import es.idynamicsax.idax.repository.TenantUserDataAreaRepository;
import es.idynamicsax.idax.service.permission.ModulePermissionCatalogDescriptor;
import es.idynamicsax.idax.service.permission.ModulePermissionCatalogLifecycle;
import es.idynamicsax.idax.service.permission.ModulePermissionCatalogParser;
import es.idynamicsax.idax.service.permission.ModulePermissionCatalogRegistrar;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@Configuration
@EntityScan(basePackageClasses = IdaxPermission.class)
@EnableJpaRepositories(
        basePackageClasses = IdaxPermissionRepository.class,
        includeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = {
                        IdaxPermissionRepository.class,
                        IdaxAuditEventRepository.class,
                        TenantLegacyRepository.class,
                        TenantUserDataAreaRepository.class
                }),
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.REGEX,
                pattern = "es\\.idynamicsax\\.idax\\.repository\\.(?!(IdaxPermissionRepository|IdaxAuditEventRepository|TenantLegacyRepository|TenantUserDataAreaRepository)$).*")
)
@Import({
        ModulePermissionCatalogParser.class,
        ModulePermissionCatalogRegistrar.class,
        ModulePermissionCatalogLifecycle.class
})
public class LedgerPermissionCatalogConfiguration {
    @Bean
    ModulePermissionCatalogDescriptor ledgerPermissionCatalogDescriptor() {
        return new ModulePermissionCatalogDescriptor("ledger");
    }
}
