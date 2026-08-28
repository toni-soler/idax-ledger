package es.idynamicsax.ledger.provider;
public record SubmittedAnchor(String transactionHash,long ledgerIndex,String ledgerHash,String transactionResult){}
