package es.idynamicsax.ledger.xrpl;

import com.fasterxml.jackson.databind.JsonNode;
import es.idynamicsax.ledger.config.LedgerProperties;
import es.idynamicsax.ledger.provider.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HexFormat;
import com.google.common.primitives.UnsignedInteger;
import com.google.common.primitives.UnsignedLong;
import org.xrpl.xrpl4j.crypto.keys.Base58EncodedSecret;
import org.xrpl.xrpl4j.crypto.keys.Seed;
import org.xrpl.xrpl4j.crypto.signing.bc.BcSignatureService;
import org.xrpl.xrpl4j.model.flags.AccountSetTransactionFlags;
import org.xrpl.xrpl4j.model.transactions.*;
import org.springframework.stereotype.Component;

@Component
public class XrplLedgerProvider implements LedgerProvider {
    public static final String TYPE = "XRPL";
    private static final long RIPPLE_EPOCH_OFFSET = 946684800L;
    private static final long TF_FULLY_CANONICAL_SIG = 0x80000000L;
    private final XrplRpcClient client;
    private final LedgerProperties properties;
    private final Object anchorLock = new Object();

    public XrplLedgerProvider(XrplRpcClient client, LedgerProperties properties) {
        this.client = client;
        this.properties = properties;
    }

    @Override public String providerType() { return TYPE; }

    @Override
    public LedgerNetworkStatus getNetworkStatus(LedgerProperties.Network network) {
        Instant observedAt = Instant.now();
        List<NodeObservation> observations = network.nodes().stream().map(node -> observe(node, observedAt)).toList();
        List<NodeObservation> healthy = observations.stream().filter(observation -> observation.status().healthy()).toList();
        long minimum = healthy.stream().map(NodeObservation::status).map(LedgerNodeStatus::validatedLedgerIndex)
                .filter(java.util.Objects::nonNull).mapToLong(Long::longValue).min().orElse(-1);
        long maximum = healthy.stream().map(NodeObservation::status).map(LedgerNodeStatus::validatedLedgerIndex)
                .filter(java.util.Objects::nonNull).mapToLong(Long::longValue).max().orElse(-1);
        long maximumAge = observations.stream().mapToLong(NodeObservation::ledgerAgeSeconds).max().orElse(Long.MAX_VALUE);
        boolean current = maximumAge <= properties.maximumLedgerAge().toSeconds();
        boolean aligned = minimum >= 0 && maximum - minimum <= 1;
        LedgerNetworkStatus.Health health = healthy.isEmpty() ? LedgerNetworkStatus.Health.UNAVAILABLE
                : healthy.size() == network.nodes().size() && current && aligned
                ? LedgerNetworkStatus.Health.HEALTHY : LedgerNetworkStatus.Health.DEGRADED;
        NodeObservation latest = healthy.stream().max(java.util.Comparator.comparingLong(
                item -> item.status().validatedLedgerIndex())).orElse(null);
        return new LedgerNetworkStatus(network.id(), TYPE, health,
                latest == null ? null : latest.status().validatedLedgerIndex(),
                latest == null ? null : latest.status().validatedLedgerHash(),
                healthy.size(), network.nodes().size(),
                latest == null ? null : observedAt.minusSeconds(latest.ledgerAgeSeconds()),
                observedAt, health == LedgerNetworkStatus.Health.HEALTHY ? "Consensus nodes aligned" : "One or more XRPL health conditions failed");
    }

    @Override
    public List<LedgerNodeStatus> getNodeStatuses(LedgerProperties.Network network) {
        Instant observedAt = Instant.now();
        List<LedgerNodeStatus> statuses = new ArrayList<>();
        for (var node : network.nodes()) {
            try { statuses.add(observe(node, observedAt).status()); }
            catch (LedgerProviderException exception) {
                statuses.add(new LedgerNodeStatus(node.id(), "unavailable", 0, null, null, null, null, observedAt));
            }
        }
        return List.copyOf(statuses);
    }

    @Override
    public Optional<LedgerView> getLedger(LedgerProperties.Network network, long ledgerIndex) {
        JsonNode result = client.call(primary(network).rpcUrl(), "ledger", Map.of(
                "ledger_index", ledgerIndex, "transactions", true, "expand", false));
        JsonNode ledger = result.path("ledger");
        if (ledger.isMissingNode() || ledger.isNull()) return Optional.empty();
        return Optional.of(new LedgerView(network.id(), ledger.path("ledger_index").asLong(ledgerIndex),
                ledger.path("ledger_hash").asText(null), ledger.path("parent_hash").asText(null),
                rippleTime(ledger.path("close_time")), ledger.path("transactions").size(),
                result.path("validated").asBoolean(false)));
    }

    @Override
    public Optional<LedgerTransactionView> getTransaction(LedgerProperties.Network network, String hash) {
        try {
            JsonNode result = client.call(primary(network).rpcUrl(), "tx", Map.of("transaction", hash, "binary", false));
            JsonNode tx = result.has("tx_json") ? result.path("tx_json") : result;
            String actualHash = result.path("hash").asText(tx.path("hash").asText(hash));
            LedgerTransactionView.ProofMemo proofMemo=decodeProofMemo(tx);
            return Optional.of(new LedgerTransactionView(actualHash, network.id(), nullableLong(result, "ledger_index"),
                    result.path("validated").asBoolean(false), tx.path("TransactionType").asText(null),
                    tx.path("Account").asText(null), nullableLong(tx, "Sequence"),
                    nullableLong(tx,"NetworkID"),result.path("meta").path("TransactionResult").asText(null), proofMemo,Instant.now()));
        } catch (LedgerProviderException exception) {
            if (exception.getMessage() != null && exception.getMessage().toLowerCase().contains("not found")) return Optional.empty();
            throw exception;
        }
    }

    @Override
    public PreparedAnchor prepareAnchor(LedgerProperties.Network network, java.util.UUID publicId, String contentHash) {
        synchronized (anchorLock) {
            requireAnchorNetwork(network);
            try {
                String seedText=Files.readString(Path.of(properties.anchoring().seedFile())).trim();
                var keyPair=Seed.fromBase58EncodedSecret(Base58EncodedSecret.of(seedText)).deriveKeyPair();
                var privateKey=keyPair.privateKey();
                var address=keyPair.publicKey().deriveAddress();
                if(!address.value().equals(properties.anchoring().account()))throw new LedgerProviderException("Anchoring seed does not match configured account");
                JsonNode account=client.call(primary(network).rpcUrl(),"account_info",Map.of("account",address.value(),"ledger_index","validated"));
                JsonNode server=client.call(primary(network).rpcUrl(),"server_info",Map.of()).path("info");
                validateServerNetworkId(network, server.path("network_id").isNumber() ? server.path("network_id").asLong() : null);
                long sequence=account.path("account_data").path("Sequence").asLong();
                long currentLedger=server.path("validated_ledger").path("seq").asLong();
                long lastLedger=currentLedger+properties.anchoring().lastLedgerOffset();
                String proofWire=es.idynamicsax.ledger.proof.ProofCanonicalizer.proofV1(publicId,contentHash);
                Memo memo=Memo.builder().memoType(hex("urn:idax:ledger:proof:v1"))
                        .memoFormat(hex("application/jcs+json")).memoData(hex(proofWire)).build();
                AccountSet transaction=AccountSet.builder().account(address).fee(XrpCurrencyAmount.of(UnsignedLong.valueOf(10)))
                        .sequence(UnsignedInteger.valueOf(sequence)).lastLedgerSequence(UnsignedInteger.valueOf(lastLedger))
                        .networkId(NetworkId.of(UnsignedInteger.valueOf(network.networkId())))
                        .flags(AccountSetTransactionFlags.of(TF_FULLY_CANONICAL_SIG))
                        .signingPublicKey(keyPair.publicKey())
                        .addMemos(MemoWrapper.builder().memo(memo).build()).build();
                var signed=new BcSignatureService().sign(privateKey,transaction);
                return new PreparedAnchor(signed.hash().value(),signed.signedTransactionBytes().hexValue(),sequence,lastLedger,network.networkId(),address.value());
            } catch(LedgerProviderException e){throw e;} catch(Exception e){throw new LedgerProviderException("Could not prepare XRPL anchor",e);}
        }
    }

    @Override
    public void submitSignedAnchor(LedgerProperties.Network network, PreparedAnchor prepared) {
        requireAnchorNetwork(network);
        JsonNode result=client.call(primary(network).rpcUrl(),"submit",Map.of("tx_blob",prepared.signedBlob()));
        String engine=result.path("engine_result").asText();
        if(!"tesSUCCESS".equals(engine))throw new LedgerProviderException("XRPL submit rejected: "+engine);
    }

    @Override
    public SubmittedAnchor awaitAnchorValidation(LedgerProperties.Network network, PreparedAnchor prepared) {
        requireAnchorNetwork(network);
        try {
            for(int attempt=0;attempt<30;attempt++){
                try{
                    JsonNode tx=client.call(primary(network).rpcUrl(),"tx",Map.of("transaction",prepared.transactionHash(),"binary",false));
                    if(tx.path("validated").asBoolean(false)){
                        String txResult=tx.path("meta").path("TransactionResult").asText();
                        if(!"tesSUCCESS".equals(txResult))throw new LedgerProviderException("XRPL transaction failed: "+txResult);
                        long index=tx.path("ledger_index").asLong();
                        JsonNode ledger=client.call(primary(network).rpcUrl(),"ledger",Map.of("ledger_index",index));
                        return new SubmittedAnchor(prepared.transactionHash(),index,ledger.path("ledger_hash").asText(),txResult);
                    }
                }catch(LedgerProviderException e){if(attempt==29)throw e;}
                Thread.sleep(1000);
            }
            throw new LedgerProviderException("XRPL validation timeout");
        }catch(InterruptedException e){Thread.currentThread().interrupt();throw new LedgerProviderException("XRPL validation interrupted",e);}
    }

    @Override
    public AnchorTransactionStatus inspectAnchor(LedgerProperties.Network network, PreparedAnchor prepared) {
        requireAnchorNetwork(network);
        try {
            JsonNode tx=client.call(primary(network).rpcUrl(),"tx",Map.of("transaction",prepared.transactionHash(),"binary",false));
            boolean validated=tx.path("validated").asBoolean(false); String result=tx.path("meta").path("TransactionResult").asText(null);
            if(!validated)return new AnchorTransactionStatus(AnchorTransactionStatus.State.FOUND_NOT_VALIDATED,result,null,null);
            long index=tx.path("ledger_index").asLong();
            JsonNode ledger=client.call(primary(network).rpcUrl(),"ledger",Map.of("ledger_index",index));
            String hash=ledger.path("ledger_hash").asText(null);
            return new AnchorTransactionStatus("tesSUCCESS".equals(result)?AnchorTransactionStatus.State.VALIDATED_SUCCESS:AnchorTransactionStatus.State.VALIDATED_FAILURE,result,index,hash);
        } catch(LedgerProviderException e){
            if(e.getMessage()!=null&&e.getMessage().toLowerCase().contains("not found"))return new AnchorTransactionStatus(AnchorTransactionStatus.State.NOT_FOUND,null,null,null);
            throw e;
        }
    }

    @Override public long currentValidatedLedger(LedgerProperties.Network network){
        JsonNode info=client.call(primary(network).rpcUrl(),"server_info",Map.of()).path("info"); validateServerNetworkId(network,nullableLong(info,"network_id")); return info.path("validated_ledger").path("seq").asLong();
    }

    @Override
    public AnchorVerification verifyAnchor(LedgerProperties.Network network, java.util.UUID publicId, String contentHash, String transactionHash, String expectedLedgerHash){
        return verifyAnchor(network,publicId,contentHash,transactionHash,expectedLedgerHash,null);
    }
    @Override
    public AnchorVerification verifyAnchor(LedgerProperties.Network network, java.util.UUID publicId, String contentHash, String transactionHash, String expectedLedgerHash,String expectedAccount){
        requireAnchorNetwork(network);
        try{
            JsonNode result=client.call(primary(network).rpcUrl(),"tx",Map.of("transaction",transactionHash,"binary",false));
            JsonNode tx=result.has("tx_json")?result.path("tx_json"):result;
            long ledgerIndex=result.path("ledger_index").asLong();
            JsonNode ledger=client.call(primary(network).rpcUrl(),"ledger",Map.of("ledger_index",ledgerIndex,"transactions",true,"expand",false));
            String ledgerHash=ledger.path("ledger_hash").asText();
            boolean transactionIncluded=false;
            for(JsonNode entry:ledger.path("ledger").path("transactions")){if(transactionHash.equalsIgnoreCase(entry.isTextual()?entry.asText():entry.path("hash").asText())){transactionIncluded=true;break;}}
            boolean independentlyValidated=result.path("validated").asBoolean(false)||(ledger.path("validated").asBoolean(false)&&transactionIncluded);
            if(!independentlyValidated)return new AnchorVerification(AnchorVerification.Status.NOT_VALIDATED,"Transaction is not included in a validated ledger",ledgerIndex,ledgerHash);
            String expectedWire=es.idynamicsax.ledger.proof.ProofCanonicalizer.proofV1(publicId,contentHash);
            JsonNode memo=tx.path("Memos").path(0).path("Memo");
            boolean match="AccountSet".equals(tx.path("TransactionType").asText())
                    && (expectedAccount==null||expectedAccount.equals(tx.path("Account").asText()))
                    && network.networkId()==tx.path("NetworkID").asLong()
                    && hex("urn:idax:ledger:proof:v1").equalsIgnoreCase(memo.path("MemoType").asText())
                    && hex("application/jcs+json").equalsIgnoreCase(memo.path("MemoFormat").asText())
                    && expectedWire.equals(new String(HexFormat.of().parseHex(memo.path("MemoData").asText()),StandardCharsets.UTF_8))
                    && "tesSUCCESS".equals(result.path("meta").path("TransactionResult").asText())
                    && transactionIncluded
                    && (expectedLedgerHash==null||expectedLedgerHash.equals(ledgerHash));
            return new AnchorVerification(match?AnchorVerification.Status.VALIDATED_MATCH:AnchorVerification.Status.ANCHOR_MISMATCH,
                    match?"XRPL transaction, ledger and Proof v1 memo match":"Stored proof does not match validated XRPL anchor",ledgerIndex,ledgerHash);
        }catch(LedgerProviderException e){if(e.getMessage()!=null&&e.getMessage().toLowerCase().contains("not found"))return new AnchorVerification(AnchorVerification.Status.NOT_FOUND,e.getMessage(),null,null);return new AnchorVerification(AnchorVerification.Status.PROVIDER_UNAVAILABLE,e.getMessage(),null,null);}
    }

    private void requireAnchorNetwork(LedgerProperties.Network network){if(network.networkId()==null||network.networkId()<1025||!network.id().equals(properties.anchoring().network()))throw new LedgerProviderException("Invalid anchoring NetworkID configuration");}
    void validateServerNetworkId(LedgerProperties.Network network,Long serverNetworkId){requireAnchorNetwork(network);if(serverNetworkId==null)throw new LedgerProviderException("XRPL server did not report NetworkID");if(!serverNetworkId.equals(network.networkId()))throw new LedgerProviderException("XRPL server is on a different NetworkID");}
    private static String hex(String value){return HexFormat.of().withUpperCase().formatHex(value.getBytes(StandardCharsets.UTF_8));}
    private LedgerTransactionView.ProofMemo decodeProofMemo(JsonNode tx){try{JsonNode memo=tx.path("Memos").path(0).path("Memo");if(!hex("urn:idax:ledger:proof:v1").equalsIgnoreCase(memo.path("MemoType").asText()))return null;String wire=new String(HexFormat.of().parseHex(memo.path("MemoData").asText()),StandardCharsets.UTF_8);JsonNode value=new com.fasterxml.jackson.databind.ObjectMapper().readTree(wire);return new LedgerTransactionView.ProofMemo(value.path("f").asText(),value.path("v").asInt(),value.path("i").asText(),value.path("d").asText());}catch(Exception ignored){return null;}}

    private NodeObservation observe(LedgerProperties.Node node, Instant observedAt) {
        JsonNode info = client.call(node.rpcUrl(), "server_info", Map.of()).path("info");
        JsonNode ledger = info.path("validated_ledger");
        return new NodeObservation(new LedgerNodeStatus(node.id(), info.path("server_state").asText("unknown"),
                info.path("peers").asInt(0), nullableLong(ledger, "seq"), ledger.path("hash").asText(null),
                info.path("complete_ledgers").asText(null), info.path("pubkey_validator").asText(null), observedAt),
                info.path("validated_ledger").path("age").asLong(Long.MAX_VALUE));
    }

    private LedgerProperties.Node primary(LedgerProperties.Network network) {
        return network.nodes().stream().findFirst().orElseThrow(() -> new LedgerProviderException("XRPL network has no configured nodes"));
    }

    private Instant rippleTime(JsonNode node) { return node.isNumber() ? Instant.ofEpochSecond(node.asLong() + RIPPLE_EPOCH_OFFSET) : null; }
    private Long nullableLong(JsonNode node, String field) { return node.hasNonNull(field) ? node.path(field).asLong() : null; }
    private record NodeObservation(LedgerNodeStatus status, long ledgerAgeSeconds) {}
}
