package es.idynamicsax.ledger.service;

import es.idynamicsax.ledger.config.LedgerProperties;
import es.idynamicsax.ledger.provider.*;
import java.util.List;
import java.util.NoSuchElementException;
import org.springframework.stereotype.Service;

@Service
public class LedgerService {
    private final LedgerProperties properties;
    private final LedgerProviderRegistry providers;

    public LedgerService(LedgerProperties properties, LedgerProviderRegistry providers) {
        this.properties = properties;
        this.providers = providers;
    }

    public List<LedgerNetworkView> networks() {
        return properties.networks().stream().map(this::view).toList();
    }

    public LedgerNetworkView network(String networkId) { return view(requireNetwork(networkId)); }

    public LedgerNetworkStatus status(String networkId) {
        var network = requireEnabledNetwork(networkId);
        return providers.require(network.providerType()).getNetworkStatus(network);
    }

    public List<LedgerNodeStatus> nodes(String networkId) {
        var network = requireEnabledNetwork(networkId);
        return providers.require(network.providerType()).getNodeStatuses(network);
    }

    public LedgerView ledger(String networkId, long ledgerIndex) {
        var network = requireEnabledNetwork(networkId);
        return providers.require(network.providerType()).getLedger(network, ledgerIndex)
                .orElseThrow(() -> new NoSuchElementException("Ledger not found: " + ledgerIndex));
    }

    public LedgerTransactionView transaction(String networkId, String hash) {
        var network = requireEnabledNetwork(networkId);
        return providers.require(network.providerType()).getTransaction(network, hash)
                .orElseThrow(() -> new NoSuchElementException("Ledger transaction not found: " + hash));
    }

    private LedgerProperties.Network requireNetwork(String id) {
        return properties.networks().stream().filter(network -> network.id().equals(id)).findFirst()
                .orElseThrow(() -> new NoSuchElementException("Ledger network not found: " + id));
    }

    private LedgerProperties.Network requireEnabledNetwork(String id) {
        var network = requireNetwork(id);
        if (!network.enabled()) throw new IllegalArgumentException("Ledger network is disabled: " + id);
        return network;
    }

    private LedgerNetworkView view(LedgerProperties.Network network) {
        return new LedgerNetworkView(network.id(), network.displayName(), network.providerType(),
                network.kind(), network.enabled(), network.networkId(), network.nodes().size());
    }
}
