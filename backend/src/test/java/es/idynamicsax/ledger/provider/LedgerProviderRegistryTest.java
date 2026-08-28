package es.idynamicsax.ledger.provider;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import java.util.List;
import java.util.Optional;
import java.util.List;
import es.idynamicsax.ledger.config.LedgerProperties;
import org.junit.jupiter.api.Test;

class LedgerProviderRegistryTest {
    @Test void resolvesProviderWithoutExposingProviderSpecificApi() {
        LedgerProvider provider = new LedgerProvider() {
            public String providerType() { return "XRPL"; }
            public LedgerNetworkStatus getNetworkStatus(LedgerProperties.Network network) { return null; }
            public List<LedgerNodeStatus> getNodeStatuses(LedgerProperties.Network network) { return List.of(); }
            public Optional<LedgerView> getLedger(LedgerProperties.Network network, long index) { return Optional.empty(); }
            public Optional<LedgerTransactionView> getTransaction(LedgerProperties.Network network, String hash) { return Optional.empty(); }
        };
        LedgerProviderRegistry registry = new LedgerProviderRegistry(List.of(provider));
        assertSame(provider, registry.require("XRPL"));
        assertThrows(IllegalArgumentException.class, () -> registry.require("BITCOIN"));
    }
}
