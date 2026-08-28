package es.idynamicsax.ledger.provider;

import java.time.Instant;

public record LedgerNetworkStatus(String networkId, String providerType, Health health,
                                  Long validatedLedgerIndex, String validatedLedgerHash,
                                  int healthyNodes, int configuredNodes,
                                  Instant lastLedgerClose, Instant observedAt, String detail) {
    public enum Health { HEALTHY, DEGRADED, UNAVAILABLE, DISABLED }
}
