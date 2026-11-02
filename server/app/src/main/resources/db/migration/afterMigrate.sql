-- Callback de Flyway: corre después de cada migrate, con el usuario de Flyway
-- (el dueño del esquema en el despliegue).
--
-- El historial de migraciones es solo del dueño. El ALTER DEFAULT PRIVILEGES
-- de deploy/postgres/10-roles.sh le da a la app SELECT, INSERT y UPDATE sobre
-- toda tabla que cree el dueño, y esta también la crea él: con eso, un proceso
-- comprometido podría dar por aplicada una migración que nunca corrió o romper
-- los checksums y tumbar el arranque siguiente.
--
-- No nombra ningún rol: quita todo permiso a quien no sea el dueño de la
-- tabla. Así no repite los nombres del script de roles, y en los tests, donde
-- no hay más rol que el dueño, no hace nada.
DO $$
DECLARE
    history regclass := to_regclass(format('%I.%I', '${flyway:defaultSchema}', '${flyway:table}'));
    grantee_name text;
BEGIN
    FOR grantee_name IN
        SELECT DISTINCT CASE WHEN acl.grantee = 0 THEN 'PUBLIC' ELSE acl.grantee::regrole::text END
        FROM pg_class AS c
        CROSS JOIN LATERAL aclexplode(c.relacl) AS acl
        WHERE c.oid = history
          AND acl.grantee <> c.relowner
    LOOP
        EXECUTE format('REVOKE ALL ON %s FROM %s', history, grantee_name);
    END LOOP;
END $$;
