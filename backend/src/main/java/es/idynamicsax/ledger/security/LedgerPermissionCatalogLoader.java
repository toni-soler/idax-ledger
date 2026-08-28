package es.idynamicsax.ledger.security;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class LedgerPermissionCatalogLoader implements ApplicationRunner {
    private final ObjectMapper objectMapper;
    private final JdbcTemplate jdbcTemplate;

    public LedgerPermissionCatalogLoader(ObjectMapper objectMapper, JdbcTemplate jdbcTemplate) {
        this.objectMapper = objectMapper;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override @Transactional
    public void run(ApplicationArguments args) throws Exception {
        var resource = new ClassPathResource("generated/ledger/permission-catalog.generated.json");
        List<Entry> entries = objectMapper.readValue(resource.getInputStream(), new TypeReference<>() {});
        for (Entry entry : entries) {
            jdbcTemplate.update("""
                    insert into idax_core.idax_permission
                      (permission_code,module_key,resource_key,action_key,label_key,api_path,description,source_type,enabled)
                    values (?,?,?,?,?,?,?,?,true)
                    on conflict (permission_code) do update set
                      module_key=excluded.module_key, resource_key=excluded.resource_key,
                      action_key=excluded.action_key, label_key=excluded.label_key,
                      api_path=excluded.api_path, description=excluded.description,
                      source_type=excluded.source_type, enabled=true
                    """, entry.code(), entry.moduleKey(), entry.resourceKey(), entry.actionKey(), entry.labelKey(),
                    entry.apiPath(), entry.description(), entry.sourceType());
        }
        jdbcTemplate.update("""
                insert into idax_core.idax_role_permission(role_id,permission_code)
                select r.role_id, p.permission_code from idax_core.idax_role r
                cross join idax_core.idax_permission p
                where r.system_role and r.role_key in ('owner','admin') and p.source_type='LEDGER'
                on conflict do nothing
                """);
    }

    record Entry(String code, String moduleKey, String resourceKey, String actionKey,
                 String labelKey, String apiPath, String description, String sourceType) {}
}
