package es.idynamicsax.ledger.service;

public record LedgerNetworkView(String id, String displayName, String providerType,
                                String kind, boolean enabled, Long networkId, int nodeCount) {}
