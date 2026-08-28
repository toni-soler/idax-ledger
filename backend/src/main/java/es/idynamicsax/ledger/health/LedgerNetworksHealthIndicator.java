package es.idynamicsax.ledger.health;

import es.idynamicsax.ledger.config.LedgerProperties;
import es.idynamicsax.ledger.provider.LedgerNetworkStatus;
import es.idynamicsax.ledger.service.LedgerService;
import java.util.LinkedHashMap;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

@Component("ledgerNetworks")
public class LedgerNetworksHealthIndicator implements HealthIndicator {
    private final LedgerProperties properties;
    private final LedgerService service;

    public LedgerNetworksHealthIndicator(LedgerProperties properties, LedgerService service) {
        this.properties = properties;
        this.service = service;
    }

    @Override public Health health() {
        var details = new LinkedHashMap<String, Object>();
        boolean up = true;
        for (var network : properties.networks()) {
            if (!network.enabled()) continue;
            try {
                var status = service.status(network.id());
                details.put(network.id(), status);
                up &= status.health() == LedgerNetworkStatus.Health.HEALTHY;
            } catch (RuntimeException exception) {
                up = false;
                details.put(network.id(), java.util.Map.of("health", "UNAVAILABLE", "detail", exception.getMessage()));
            }
        }
        return (up ? Health.up() : Health.down()).withDetails(details).build();
    }
}
