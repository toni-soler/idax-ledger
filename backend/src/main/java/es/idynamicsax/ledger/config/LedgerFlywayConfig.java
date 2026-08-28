package es.idynamicsax.ledger.config;

import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class LedgerFlywayConfig {
    @Bean
    Flyway flywayLedger(DataSource dataSource) {
        Flyway flyway = Flyway.configure().dataSource(dataSource).schemas("idax_ledger")
                .locations("classpath:db/migration-idax-ledger").baselineOnMigrate(true).load();
        flyway.migrate();
        return flyway;
    }
}
