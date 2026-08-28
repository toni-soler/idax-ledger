package es.idynamicsax.ledger.proof;

import static org.junit.jupiter.api.Assertions.assertEquals;
import com.fasterxml.jackson.databind.ObjectMapper;
import es.idynamicsax.idax.tenant.TenantContext;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@EnabledIfEnvironmentVariable(named="IDAX_LEDGER_RUN_RESTART_E2E", matches="true")
class LedgerProofRestartVerificationE2ETest {
    @Autowired LedgerProofService service;
    private final ObjectMapper mapper = new ObjectMapper();

    @AfterEach void clear() { TenantContext.clear(); }

    @Test void oldTransactionAndLedgerStillVerifyAfterXrplRestart() throws Exception {
        var evidence = mapper.readTree(Files.readString(Path.of("target", "phase5b-e2e-result.json")));
        UUID tenantId = UUID.fromString(evidence.path("tenantId").asText());
        UUID proofId = UUID.fromString(evidence.path("proofId").asText());
        TenantContext.set(new TenantContext(tenantId,"e2e",UUID.randomUUID(),"phase5b-restart-e2e",TenantContext.DbRole.IDAX_APP));
        String request = mapper.writeValueAsString(Map.of(
                "externalId", evidence.path("externalId").asText(),
                "proofType", evidence.path("proofType").asText(),
                "payload", Map.of("a", 1, "b", 2)));
        var result = service.verify(proofId, request);
        assertEquals("MATCH", result.integrity().status());
        assertEquals("VALIDATED_MATCH", result.ledger().status());
        assertEquals(evidence.path("transactionHash").asText(), result.ledger().transactionHash());
        assertEquals(evidence.path("ledgerHash").asText(), result.ledger().ledgerHash());
    }
}
