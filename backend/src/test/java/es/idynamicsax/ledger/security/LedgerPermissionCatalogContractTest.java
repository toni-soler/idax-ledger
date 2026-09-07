package es.idynamicsax.ledger.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import es.idynamicsax.idax.service.permission.ModulePermissionCatalogDescriptor;
import es.idynamicsax.idax.service.permission.ModulePermissionCatalogParser;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LedgerPermissionCatalogContractTest {
    @Test
    void packagedCatalogUsesStrictV1AndExactProductionSet() throws Exception {
        var resource = new ClassPathResource("generated/ledger/permission-catalog.generated.json");
        var catalog = new ModulePermissionCatalogParser(new ObjectMapper()).parse(resource.getInputStream().readAllBytes());

        assertThat(catalog.schemaVersion()).isEqualTo(1);
        assertThat(catalog.moduleKey()).isEqualTo("ledger");
        assertThat(catalog.sourceType()).isEqualTo("IDAX_MODULE");
        assertThat(catalog.permissions().stream().map(permission -> permission.code()).collect(Collectors.toSet()))
                .isEqualTo(Set.of("LEDGER_READ", "LEDGER_PROOF_CREATE", "LEDGER_PROOF_VERIFY"));
        assertThat(catalog.permissions()).allSatisfy(permission -> {
            assertThat(permission.resourceKey()).isNotBlank();
            assertThat(permission.actionKey()).isNotBlank();
            assertThat(permission.labelKey()).isNotBlank();
            assertThat(permission.description()).isNotBlank();
        });
    }

    @Test
    void descriptorBindsLedgerAndHistoricalLoaderIsGone() {
        assertThat(new ModulePermissionCatalogDescriptor("ledger").moduleKey()).isEqualTo("ledger");
        assertThatThrownBy(() -> Class.forName("es.idynamicsax.ledger.security.LedgerPermissionCatalogLoader"))
                .isInstanceOf(ClassNotFoundException.class);
    }
}
