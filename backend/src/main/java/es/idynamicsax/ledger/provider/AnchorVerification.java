package es.idynamicsax.ledger.provider;
public record AnchorVerification(Status status,String detail,Long ledgerIndex,String ledgerHash){public enum Status{VALIDATED_MATCH,NOT_FOUND,NOT_VALIDATED,ANCHOR_MISMATCH,PROVIDER_UNAVAILABLE}}
