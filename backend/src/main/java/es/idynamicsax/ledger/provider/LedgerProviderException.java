package es.idynamicsax.ledger.provider;

public class LedgerProviderException extends RuntimeException {
    public LedgerProviderException(String message) { super(message); }
    public LedgerProviderException(String message, Throwable cause) { super(message, cause); }
}
