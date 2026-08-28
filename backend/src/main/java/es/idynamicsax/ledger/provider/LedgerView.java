package es.idynamicsax.ledger.provider;

import java.time.Instant;

public record LedgerView(String networkId, long index, String hash, String parentHash,
                         Instant closeTime, int transactionCount, boolean validated) {}
