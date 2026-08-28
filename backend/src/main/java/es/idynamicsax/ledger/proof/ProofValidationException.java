package es.idynamicsax.ledger.proof;
public class ProofValidationException extends RuntimeException{private final String code;public ProofValidationException(String code,String message){super(message);this.code=code;}public String code(){return code;}}
