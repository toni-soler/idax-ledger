package es.idynamicsax.ledger.proof;

import es.idynamicsax.ledger.provider.LedgerProviderException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Connection;
import java.time.Duration;
import javax.sql.DataSource;
import org.springframework.stereotype.Component;

/** Session advisory lock scoped by provider, network and anchoring account. Connection death releases it. */
@Component
public class AnchoringAccountLock {
    private final DataSource dataSource;
    public AnchoringAccountLock(DataSource dataSource){this.dataSource=dataSource;}

    public Lease acquire(String scope, Duration timeout) {
        long key=key(scope); long deadline=System.nanoTime()+timeout.toNanos();
        try {
            Connection connection=dataSource.getConnection(); connection.setAutoCommit(true);
            while(System.nanoTime()<deadline){
                try(var statement=connection.prepareStatement("select pg_try_advisory_lock(?)")){
                    statement.setLong(1,key); try(var result=statement.executeQuery()){result.next();if(result.getBoolean(1))return new Lease(connection,key);}
                }
                Thread.sleep(50);
            }
            connection.close(); throw new LedgerProviderException("Timed out acquiring anchoring account advisory lock");
        }catch(InterruptedException e){Thread.currentThread().interrupt();throw new LedgerProviderException("Anchoring account lock interrupted",e);}
        catch(LedgerProviderException e){throw e;}catch(Exception e){throw new LedgerProviderException("Could not acquire anchoring account advisory lock",e);}
    }

    static long key(String scope){try{return ByteBuffer.wrap(MessageDigest.getInstance("SHA-256").digest(scope.getBytes(StandardCharsets.UTF_8))).getLong();}catch(Exception e){throw new IllegalStateException(e);}}
    public static class Lease implements AutoCloseable {
        private final Connection connection; private final long key;
        Lease(Connection connection,long key){this.connection=connection;this.key=key;}
        @Override public void close(){try(var statement=connection.prepareStatement("select pg_advisory_unlock(?)")){statement.setLong(1,key);statement.execute();}catch(Exception ignored){}finally{try{connection.close();}catch(Exception ignored){}}}
    }
}
