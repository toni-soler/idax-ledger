package es.idynamicsax.ledger.proof;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.json.JsonMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import org.erdtman.jcs.JsonCanonicalizer;
import org.springframework.stereotype.Component;

@Component
public class ProofCanonicalizer {
    public static final String JCS_PROFILE="JCS-RFC8785-UTF8-V1";
    private final ObjectMapper strict = JsonMapper.builder()
            .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
            .enable(DeserializationFeature.USE_BIG_INTEGER_FOR_INTS).build();

    public JsonNode parseRequest(String json){
        try{return strict.readTree(json);}catch(Exception e){throw new ProofValidationException("INVALID_JSON",e.getMessage());}
    }
    public Canonicalized canonicalize(JsonNode payload){
        if(payload==null||payload.isMissingNode()||payload.isNull())throw new ProofValidationException("PAYLOAD_REQUIRED","payload is required");
        validate(payload);
        try{
            byte[] bytes=new JsonCanonicalizer(payload.toString()).getEncodedUTF8();
            return new Canonicalized(bytes,sha256(bytes));
        }catch(Exception e){throw new ProofValidationException("INVALID_I_JSON",e.getMessage());}
    }
    public static String sha256(byte[] bytes){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));}catch(Exception e){throw new IllegalStateException(e);}}
    public static String proofV1(java.util.UUID publicId,String digest){
        return "{\"d\":\""+digest+"\",\"f\":\"IDAX_LEDGER_PROOF\",\"i\":\""+publicId.toString().toLowerCase()+"\",\"v\":1}";
    }
    private void validate(JsonNode node){
        if(node.isNumber()){
            double value=node.doubleValue();
            if(!Double.isFinite(value))throw new ProofValidationException("INVALID_I_JSON","Non-finite number");
            if(Double.doubleToRawLongBits(value)==Double.doubleToRawLongBits(-0.0d))throw new ProofValidationException("INVALID_I_JSON","Negative zero is not supported");
            if(node.isIntegralNumber() && !node.bigIntegerValue().equals(java.math.BigDecimal.valueOf(value).toBigInteger()))throw new ProofValidationException("INVALID_I_JSON","Integer is outside exact IEEE-754 range");
        }
        if(node.isTextual()){
            String value=node.textValue();
            for(int i=0;i<value.length();i++)if(Character.isSurrogate(value.charAt(i))&&(i+1>=value.length()||!Character.isSurrogatePair(value.charAt(i),value.charAt(++i))))throw new ProofValidationException("INVALID_I_JSON","Invalid Unicode surrogate");
        }
        node.elements().forEachRemaining(this::validate);
    }
    public record Canonicalized(byte[] bytes,String hash){}
}
