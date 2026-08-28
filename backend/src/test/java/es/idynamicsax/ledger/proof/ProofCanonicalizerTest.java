package es.idynamicsax.ledger.proof;
import static org.junit.jupiter.api.Assertions.*; import java.nio.charset.StandardCharsets; import org.junit.jupiter.api.Test;
class ProofCanonicalizerTest{
 private final ProofCanonicalizer subject=new ProofCanonicalizer();
 private String hash(String json){return subject.canonicalize(subject.parseRequest(json)).hash();}
 @Test void equivalentObjectsUseRfc8785Ordering(){assertEquals(hash("{\"b\":2,\"a\":1}"),hash("{\"a\":1,\"b\":2}"));assertEquals(hash("{\"z\":[3,2,1],\"a\":{\"y\":2,\"x\":1}}"),hash("{\"a\":{\"x\":1,\"y\":2},\"z\":[3,2,1]}"));}
 @Test void officialRfc8785SampleCanonicalizesInteroperably(){var value=subject.canonicalize(subject.parseRequest("{\"numbers\":[333333333.33333329,1E30,4.50,2e-3,0.000000000000000000000000001],\"string\":\"€$\\u000f\\nA'B\\\"\\\\\\\"/\"}"));assertTrue(new String(value.bytes(),StandardCharsets.UTF_8).contains("333333333.3333333"));}
 @Test void unicodeArraysAndDifferentValuesAreStable(){assertEquals(hash("{\"é\":[\"漢字\",true,null]}"),hash("{\"é\":[\"漢字\",true,null]}"));assertNotEquals(hash("[1,2]"),hash("[2,1]"));}
 @Test void rejectsDuplicateKeysAndUnsupportedNumbers(){assertThrows(ProofValidationException.class,()->subject.parseRequest("{\"a\":1,\"a\":2}"));assertThrows(ProofValidationException.class,()->subject.parseRequest("NaN"));assertThrows(ProofValidationException.class,()->subject.parseRequest("Infinity"));assertThrows(ProofValidationException.class,()->hash("9007199254740993"));assertThrows(ProofValidationException.class,()->hash("-0.0"));}
}
