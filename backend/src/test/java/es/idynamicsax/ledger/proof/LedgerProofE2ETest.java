package es.idynamicsax.ledger.proof;

import static org.junit.jupiter.api.Assertions.*;
import es.idynamicsax.idax.tenant.*;
import java.nio.file.*; import java.util.*; import java.util.concurrent.*;
import org.junit.jupiter.api.*; import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired; import org.springframework.boot.test.context.SpringBootTest; import org.springframework.jdbc.core.JdbcTemplate; import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT)
@EnabledIfEnvironmentVariable(named="IDAX_LEDGER_RUN_E2E",matches="true")
class LedgerProofE2ETest{
 @Autowired LedgerProofService service; @Autowired JdbcTemplate jdbc; @Autowired TransactionTemplate transactions; @Autowired DbSessionContextService dbContext;
 private UUID tenantA,tenantB;
 @BeforeEach void tenants(){var ids=jdbc.queryForList("select tenant_id from idax_core.tenant order by tenant_id limit 2",UUID.class);assertTrue(ids.size()>=2);tenantA=ids.get(0);tenantB=ids.get(1);}
 @AfterEach void clear(){TenantContext.clear();}
 @Test void proofAnchorIdempotencyTenantConcurrencyAndIndependentVerification()throws Exception{
   String run=UUID.randomUUID().toString();use(tenantA);String key="phase5b-"+run;String request=request("e2e-main-"+run,Map.of("b",2,"a",1));
   var created=service.create(key,request);assertEquals(ProofStatus.VALIDATED,created.status());assertNotNull(created.submission().transactionHash());
   var retry=service.create(key,request);assertEquals(created.id(),retry.id());assertEquals(created.publicId(),retry.publicId());assertEquals(created.submission().id(),retry.submission().id());assertEquals(created.submission().transactionHash(),retry.submission().transactionHash());
   assertThrows(IdempotencyConflictException.class,()->service.create(key,request("e2e-main-"+run,Map.of("a",999))));
   var verified=service.verify(created.id(),request("e2e-main-"+run,Map.of("a",1,"b",2)));assertEquals("MATCH",verified.integrity().status());assertEquals("VALIDATED_MATCH",verified.ledger().status());
   use(tenantB);assertThrows(ProofNotFoundException.class,()->service.get(created.id()));assertThrows(ProofNotFoundException.class,()->service.verify(created.id(),null));
   var tenantBProof=service.create("phase5b-b-"+run,request("tenant-b-"+run,Map.of("tenant","B")));assertEquals(ProofStatus.VALIDATED,tenantBProof.status());
   use(tenantA);assertThrows(ProofNotFoundException.class,()->service.get(tenantBProof.id()));

   ExecutorService pool=Executors.newFixedThreadPool(5);List<Future<ProofViews.Proof>> futures=new ArrayList<>();
   for(int i=0;i<5;i++){int n=i;futures.add(pool.submit(()->{use(tenantA);try{return service.create("phase5b-concurrent-"+run+"-"+n,request("concurrent-"+run+"-"+n,Map.of("n",n)));}finally{TenantContext.clear();}}));}
   Set<String> hashes=new HashSet<>();Set<UUID> ids=new HashSet<>();for(var future:futures){var proof=future.get(90,TimeUnit.SECONDS);assertEquals(ProofStatus.VALIDATED,proof.status());ids.add(proof.id());hashes.add(proof.submission().transactionHash());}pool.shutdownNow();assertEquals(5,ids.size());assertEquals(5,hashes.size());

   long hidden=transactions.execute(status->{use(tenantB);dbContext.applyFromTenantContext();return jdbc.queryForObject("select count(*) from idax_ledger.ledger_proof where id=?",Long.class,created.id());});assertEquals(0,hidden);
   String original=created.contentHash();String altered="0".repeat(64);jdbc.update("update idax_ledger.ledger_proof set content_hash=? where id=?",altered,created.id());
   use(tenantA);assertEquals("ANCHOR_MISMATCH",service.verify(created.id(),null).ledger().status());jdbc.update("update idax_ledger.ledger_proof set content_hash=? where id=?",original,created.id());
   var finalVerification=service.verify(created.id(),request("e2e-main-"+run,Map.of("b",2,"a",1)));assertEquals("VALIDATED_MATCH",finalVerification.ledger().status());
   String evidence="{\"tenantId\":\""+tenantA+"\",\"proofId\":\""+created.id()+"\",\"publicId\":\""+created.publicId()+"\",\"externalId\":\""+created.externalId()+"\",\"proofType\":\""+created.proofType()+"\",\"contentHash\":\""+created.contentHash()+"\",\"canonicalizationProfile\":\""+created.canonicalizationProfile()+"\",\"transactionHash\":\""+created.submission().transactionHash()+"\",\"ledgerIndex\":"+created.submission().ledgerIndex()+",\"ledgerHash\":\""+created.submission().ledgerHash()+"\"}";
   Files.writeString(Path.of("target","phase5b-e2e-result.json"),evidence);
 }
 private void use(UUID tenant){TenantContext.set(new TenantContext(tenant,"e2e",UUID.randomUUID(),"phase5b-e2e",TenantContext.DbRole.IDAX_APP));}
 private String request(String external,Object payload){try{return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(Map.of("externalId",external,"proofType","E2E","payload",payload));}catch(Exception e){throw new RuntimeException(e);}}
}
