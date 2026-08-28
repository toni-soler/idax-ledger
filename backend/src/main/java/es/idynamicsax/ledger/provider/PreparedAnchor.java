package es.idynamicsax.ledger.provider;
public record PreparedAnchor(String transactionHash,String signedBlob,long sequence,long lastLedgerSequence,long networkId,String anchoringAccount){}
