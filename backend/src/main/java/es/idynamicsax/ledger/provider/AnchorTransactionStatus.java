package es.idynamicsax.ledger.provider;

public record AnchorTransactionStatus(State state, String resultCode, Long ledgerIndex, String ledgerHash) {
    public enum State { VALIDATED_SUCCESS, VALIDATED_FAILURE, FOUND_NOT_VALIDATED, NOT_FOUND }
}
