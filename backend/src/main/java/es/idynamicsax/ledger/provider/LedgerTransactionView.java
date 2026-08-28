package es.idynamicsax.ledger.provider;

import java.time.Instant;

public record LedgerTransactionView(String hash, String networkId, Long ledgerIndex,
                                    boolean validated, String transactionType, String sourceAccount,
                                    Long sequence, Long transactionNetworkId, String resultCode,
                                    ProofMemo proofMemo, Instant observedAt) {
    public record ProofMemo(String format, Integer version, String publicId, String digest) {}
}
