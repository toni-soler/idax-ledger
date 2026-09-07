DO $$
DECLARE
    unexpected_count integer;
BEGIN
    SELECT count(*) INTO unexpected_count
    FROM idax_core.idax_permission
    WHERE permission_code IN ('LEDGER_READ', 'LEDGER_PROOF_CREATE', 'LEDGER_PROOF_VERIFY')
      AND NOT (
          (module_key = 'LEDGER' AND source_type = 'LEDGER')
          OR (module_key = 'ledger' AND source_type = 'IDAX_MODULE')
      );

    IF unexpected_count <> 0 THEN
        RAISE EXCEPTION 'Cannot adopt Ledger permissions with unexpected ownership';
    END IF;

    UPDATE idax_core.idax_permission
       SET module_key = 'ledger',
           source_type = 'IDAX_MODULE',
           updated_at = now()
     WHERE permission_code IN ('LEDGER_READ', 'LEDGER_PROOF_CREATE', 'LEDGER_PROOF_VERIFY')
       AND module_key = 'LEDGER'
       AND source_type = 'LEDGER';
END $$;
