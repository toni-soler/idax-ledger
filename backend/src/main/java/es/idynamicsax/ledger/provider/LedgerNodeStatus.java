package es.idynamicsax.ledger.provider;

import java.time.Instant;

public record LedgerNodeStatus(String nodeId, String state, int peers,
                               Long validatedLedgerIndex, String validatedLedgerHash,
                               String completeLedgers, String validatorPublicKey, Instant observedAt) {
    public boolean healthy() {
        return ("proposing".equals(state) || "full".equals(state))
                && peers > 0 && validatedLedgerIndex != null
                && completeLedgers != null && !"empty".equals(completeLedgers);
    }
}
