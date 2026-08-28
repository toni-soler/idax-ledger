package es.idynamicsax.ledger.provider;

import java.util.Optional;
import java.util.List;
import es.idynamicsax.ledger.config.LedgerProperties;

public interface LedgerProvider {
    String providerType();
    LedgerNetworkStatus getNetworkStatus(LedgerProperties.Network network);
    List<LedgerNodeStatus> getNodeStatuses(LedgerProperties.Network network);
    Optional<LedgerView> getLedger(LedgerProperties.Network network, long ledgerIndex);
    Optional<LedgerTransactionView> getTransaction(LedgerProperties.Network network, String transactionHash);
    default PreparedAnchor prepareAnchor(LedgerProperties.Network network, java.util.UUID publicId, String contentHash) { throw new UnsupportedOperationException(); }
    default void submitSignedAnchor(LedgerProperties.Network network, PreparedAnchor prepared) { throw new UnsupportedOperationException(); }
    default SubmittedAnchor awaitAnchorValidation(LedgerProperties.Network network, PreparedAnchor prepared) { throw new UnsupportedOperationException(); }
    default AnchorTransactionStatus inspectAnchor(LedgerProperties.Network network, PreparedAnchor prepared) { throw new UnsupportedOperationException(); }
    default long currentValidatedLedger(LedgerProperties.Network network) { throw new UnsupportedOperationException(); }
    default AnchorVerification verifyAnchor(LedgerProperties.Network network, java.util.UUID publicId, String contentHash, String transactionHash, String expectedLedgerHash) { throw new UnsupportedOperationException(); }
    default AnchorVerification verifyAnchor(LedgerProperties.Network network, java.util.UUID publicId, String contentHash, String transactionHash, String expectedLedgerHash,String expectedAccount) { return verifyAnchor(network,publicId,contentHash,transactionHash,expectedLedgerHash); }
}
