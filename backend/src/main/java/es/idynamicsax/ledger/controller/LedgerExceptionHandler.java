package es.idynamicsax.ledger.controller;

import es.idynamicsax.idax.exception.ApiError;
import es.idynamicsax.ledger.provider.LedgerProviderException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;

@RestControllerAdvice(assignableTypes = {LedgerReadController.class, LedgerProofController.class})
public class LedgerExceptionHandler {
    @ExceptionHandler(LedgerProviderException.class)
    public ResponseEntity<ApiError> providerUnavailable(LedgerProviderException exception, WebRequest request) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(new ApiError(
                HttpStatus.BAD_GATEWAY.value(), "Ledger provider unavailable", exception.getMessage(),
                request.getDescription(false).replace("uri=", "")));
    }
    @ExceptionHandler(es.idynamicsax.ledger.proof.IdempotencyConflictException.class)
    public ResponseEntity<ApiError> idempotencyConflict(RuntimeException exception, WebRequest request) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new ApiError(409,"IDEMPOTENCY_CONFLICT",exception.getMessage(),request.getDescription(false).replace("uri=", "")));
    }
    @ExceptionHandler(es.idynamicsax.ledger.proof.ProofValidationException.class)
    public ResponseEntity<ApiError> invalidProof(es.idynamicsax.ledger.proof.ProofValidationException exception, WebRequest request) {
        return ResponseEntity.badRequest().body(new ApiError(400,exception.code(),exception.getMessage(),request.getDescription(false).replace("uri=", "")));
    }
    @ExceptionHandler(es.idynamicsax.ledger.proof.ProofNotFoundException.class)
    public ResponseEntity<ApiError> proofNotFound(RuntimeException exception, WebRequest request) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ApiError(404,"PROOF_NOT_FOUND","Proof not found",request.getDescription(false).replace("uri=", "")));
    }
}
