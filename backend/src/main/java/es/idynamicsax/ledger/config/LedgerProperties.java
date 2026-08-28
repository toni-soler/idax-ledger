package es.idynamicsax.ledger.config;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "idax.ledger")
public record LedgerProperties(Duration requestTimeout, Duration maximumLedgerAge,
                               Anchoring anchoring, Recovery recovery, List<Network> networks) {
    public LedgerProperties {
        requestTimeout = requestTimeout == null ? Duration.ofSeconds(5) : requestTimeout;
        maximumLedgerAge = maximumLedgerAge == null ? Duration.ofSeconds(30) : maximumLedgerAge;
        anchoring = anchoring == null ? new Anchoring(null, null, null, null) : anchoring;
        recovery = recovery == null ? new Recovery(null,null,null,null) : recovery;
        networks = networks == null ? List.of() : List.copyOf(networks);
    }

    public record Network(String id, String displayName, String providerType, String kind,
                          boolean enabled, Long networkId, List<Node> nodes) {
        public Network {
            nodes = nodes == null ? List.of() : List.copyOf(nodes);
        }
    }

    public record Node(String id, URI rpcUrl) {}

    public record Anchoring(String network, String account, String seedFile, Integer lastLedgerOffset) {
        public Anchoring {
            network = network == null ? "private-xrpl" : network;
            lastLedgerOffset = lastLedgerOffset == null ? 20 : lastLedgerOffset;
        }
    }
    public record Recovery(Boolean enabled,Duration interval,Duration retryDelay,Duration lockTimeout){
        public Recovery { enabled=enabled==null||enabled; interval=interval==null?Duration.ofSeconds(10):interval; retryDelay=retryDelay==null?Duration.ofSeconds(15):retryDelay; lockTimeout=lockTimeout==null?Duration.ofSeconds(10):lockTimeout; }
    }
}
