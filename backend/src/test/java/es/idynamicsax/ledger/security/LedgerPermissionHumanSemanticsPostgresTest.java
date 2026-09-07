package es.idynamicsax.ledger.security;

import es.idynamicsax.idax.repository.IdaxPermissionRepository;
import es.idynamicsax.idax.security.CurrentUser;
import es.idynamicsax.idax.service.permission.PermissionService;
import es.idynamicsax.idax.tenant.TenantContext;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.mockito.Mockito;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.DockerClientFactory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class LedgerPermissionHumanSemanticsPostgresTest {
    final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17");

    private final UUID tenantA = UUID.randomUUID();
    private final UUID tenantB = UUID.randomUUID();
    private final UUID user = UUID.randomUUID();
    private PermissionService permissions;

    @BeforeAll
    void setUp() {
        assumeTrue(DockerClientFactory.instance().isDockerAvailable(), "Docker is required for PostgreSQL integration tests");
        postgres.start();
        var dataSource = new DriverManagerDataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
        var jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("""
                create schema idax_core;
                create table idax_core.tenant_user(tenant_id uuid, user_id uuid, role varchar(40));
                create table idax_core.idax_role(role_id uuid primary key, tenant_id uuid, enabled boolean);
                create table idax_core.idax_user_role(tenant_id uuid, user_id uuid, role_id uuid);
                create table idax_core.idax_permission(permission_code varchar(160) primary key, enabled boolean);
                create table idax_core.idax_role_permission(role_id uuid, permission_code varchar(160));
                """);
        UUID role = UUID.randomUUID();
        jdbc.update("insert into idax_core.idax_role values(?,?,true)", role, tenantA);
        jdbc.update("insert into idax_core.idax_user_role values(?,?,?)", tenantA, user, role);
        jdbc.update("insert into idax_core.idax_permission values('LEDGER_READ',true),('LEDGER_PROOF_CREATE',true)");
        jdbc.update("insert into idax_core.idax_role_permission values(?,'LEDGER_READ')", role);
        permissions = new PermissionService(jdbc, Mockito.mock(IdaxPermissionRepository.class));
    }

    @AfterAll
    void stopPostgres() {
        if (postgres.isRunning()) {
            postgres.stop();
        }
    }

    @AfterEach
    void clearTenant() {
        TenantContext.clear();
    }

    @Test
    void assignedPermissionIsAllowedForNormalHuman() {
        TenantContext.set(context(tenantA));
        assertThat(permissions.hasPermission(human(tenantA), "LEDGER_READ")).isTrue();
    }

    @Test
    void missingPermissionIsDeniedForNormalHuman() {
        TenantContext.set(context(tenantA));
        assertThat(permissions.hasPermission(human(tenantA), "LEDGER_PROOF_CREATE")).isFalse();
    }

    @Test
    void roleFromAnotherTenantIsDenied() {
        TenantContext.set(context(tenantB));
        assertThat(permissions.hasPermission(human(tenantB), "LEDGER_READ")).isFalse();
    }

    private CurrentUser human(UUID tenant) {
        return new CurrentUser(user, "ledger-human", tenant, false, Set.of());
    }

    private TenantContext context(UUID tenant) {
        return new TenantContext(tenant, null, user, "ledger-human", TenantContext.DbRole.IDAX_APP);
    }
}
