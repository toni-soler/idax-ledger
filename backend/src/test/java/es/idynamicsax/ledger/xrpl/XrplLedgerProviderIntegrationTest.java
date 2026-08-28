package es.idynamicsax.ledger.xrpl;

import static org.junit.jupiter.api.Assertions.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import es.idynamicsax.ledger.config.LedgerProperties;
import es.idynamicsax.ledger.provider.LedgerNetworkStatus;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class XrplLedgerProviderIntegrationTest {
    private HttpServer server;
    private XrplLedgerProvider provider;
    private LedgerProperties.Network network;

    @BeforeEach void startServer() throws Exception {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/", exchange -> {
            String request = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            String response = request.contains("server_info") ? """
                    {"result":{"status":"success","info":{"server_state":"proposing","peers":2,
                    "complete_ledgers":"5-42","pubkey_validator":"nHU-test","validated_ledger":{"seq":42,"hash":"ABC","age":2}}}}
                    """ : request.contains("\"ledger\"") ? """
                    {"result":{"status":"success","validated":true,"ledger":{"ledger_index":42,
                    "ledger_hash":"ABC","parent_hash":"DEF","close_time":800000000,"transactions":["TX1"]}}}
                    """ : """
                    {"result":{"status":"success","validated":true,"hash":"TX1","ledger_index":42,
                    "tx_json":{"TransactionType":"Payment","Account":"rSource","Sequence":7,"NetworkID":2181844733},
                    "meta":{"TransactionResult":"tesSUCCESS"}}}
                    """;
            byte[] body = response.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        URI uri = URI.create("http://localhost:" + server.getAddress().getPort());
        var properties = new LedgerProperties(Duration.ofSeconds(2), Duration.ofSeconds(30), null, null, List.of());
        provider = new XrplLedgerProvider(new XrplRpcClient(new ObjectMapper(), properties), properties);
        network = new LedgerProperties.Network("private-xrpl", "Private XRPL", "XRPL", "PRIVATE", true,
                2181844733L, List.of(new LedgerProperties.Node("validator-01", uri)));
    }

    @AfterEach void stopServer() { server.stop(0); }

    @Test void exposesGenericReadModelsFromXrplResponses() {
        var status = provider.getNetworkStatus(network);
        assertEquals(LedgerNetworkStatus.Health.HEALTHY, status.health());
        assertEquals(42, status.validatedLedgerIndex());
        assertEquals(1, status.healthyNodes());

        var ledger = provider.getLedger(network, 42).orElseThrow();
        assertEquals("ABC", ledger.hash());
        assertEquals(1, ledger.transactionCount());
        assertTrue(ledger.validated());

        var transaction = provider.getTransaction(network, "TX1").orElseThrow();
        assertEquals("Payment", transaction.transactionType());
        assertEquals("tesSUCCESS", transaction.resultCode());
    }

    @Test void anchoringRequiresExactConfiguredNetworkId() {
        assertDoesNotThrow(() -> provider.validateServerNetworkId(network, 2181844733L));
        assertThrows(RuntimeException.class, () -> provider.validateServerNetworkId(network, null));
        assertThrows(RuntimeException.class, () -> provider.validateServerNetworkId(network, 2181844734L));
    }
}
