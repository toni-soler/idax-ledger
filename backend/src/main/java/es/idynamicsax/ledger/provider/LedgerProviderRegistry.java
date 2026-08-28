package es.idynamicsax.ledger.provider;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class LedgerProviderRegistry {
    private final Map<String, LedgerProvider> providers;

    public LedgerProviderRegistry(List<LedgerProvider> providers) {
        this.providers = providers.stream().collect(Collectors.toUnmodifiableMap(
                LedgerProvider::providerType, Function.identity()));
    }

    public LedgerProvider require(String providerType) {
        LedgerProvider provider = providers.get(providerType);
        if (provider == null) throw new IllegalArgumentException("Unsupported ledger provider: " + providerType);
        return provider;
    }
}
