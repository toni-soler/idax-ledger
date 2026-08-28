package es.idynamicsax.ledger.xrpl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import es.idynamicsax.ledger.provider.LedgerProviderException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class XrplRpcClient {
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final Duration requestTimeout;

    public XrplRpcClient(ObjectMapper objectMapper, es.idynamicsax.ledger.config.LedgerProperties properties) {
        this.objectMapper = objectMapper;
        this.requestTimeout = properties.requestTimeout();
        this.httpClient = HttpClient.newBuilder().connectTimeout(requestTimeout).build();
    }

    public JsonNode call(URI rpcUrl, String method, Map<String, Object> parameters) {
        try {
            byte[] requestBody = objectMapper.writeValueAsBytes(Map.of(
                    "method", method,
                    "params", java.util.List.of(parameters)));
            HttpRequest request = HttpRequest.newBuilder(rpcUrl)
                    .timeout(requestTimeout)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofByteArray(requestBody))
                    .build();
            HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new LedgerProviderException("XRPL node returned HTTP " + response.statusCode());
            }
            JsonNode root = objectMapper.readTree(response.body());
            JsonNode result = root.path("result");
            if (result.isMissingNode() || result.hasNonNull("error")) {
                String error = result.path("error_message").asText(result.path("error").asText("Invalid XRPL response"));
                if (result.hasNonNull("error_exception")) error += ": " + result.path("error_exception").asText();
                throw new LedgerProviderException(error);
            }
            return result;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new LedgerProviderException("XRPL node request interrupted", exception);
        } catch (LedgerProviderException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new LedgerProviderException("XRPL node request failed", exception);
        }
    }
}
