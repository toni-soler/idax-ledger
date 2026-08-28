package es.idynamicsax.ledger.xrpl;

import static org.junit.jupiter.api.Assertions.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import es.idynamicsax.ledger.config.LedgerProperties;
import es.idynamicsax.ledger.provider.LedgerNetworkStatus;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

@EnabledIfSystemProperty(named = "xrpl.live.url", matches = ".+")
class XrplLedgerProviderLiveTest {
    @Test void readsCurrentValidatedLedgerFromPrivateNetwork() {
        URI endpoint = URI.create(System.getProperty("xrpl.live.url"));
        var properties = new LedgerProperties(Duration.ofSeconds(5), Duration.ofSeconds(60), null, null, List.of());
        var provider = new XrplLedgerProvider(new XrplRpcClient(new ObjectMapper(), properties), properties);
        var network = new LedgerProperties.Network("private-xrpl", "Private XRPL", "XRPL", "PRIVATE", true,
                2181844733L, List.of(new LedgerProperties.Node("validator-live", endpoint)));
        var status = provider.getNetworkStatus(network);
        assertEquals(LedgerNetworkStatus.Health.HEALTHY, status.health());
        assertNotNull(status.validatedLedgerIndex());
        var ledger = provider.getLedger(network, status.validatedLedgerIndex()).orElseThrow();
        assertTrue(ledger.validated());
        assertEquals(status.validatedLedgerHash(), ledger.hash());
    }
}
