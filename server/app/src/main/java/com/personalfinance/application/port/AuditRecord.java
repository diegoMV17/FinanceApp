package com.personalfinance.application.port;

import com.personalfinance.domain.ledger.AccountId;
import com.personalfinance.domain.ledger.BudgetId;
import com.personalfinance.domain.ledger.PersonId;
import com.personalfinance.domain.ledger.TransactionId;
import com.personalfinance.domain.ledger.TransactionSource;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Una línea de la bitácora.
 *
 * <p>{@code before} y {@code after} se llenan solo donde el dato se perdería de
 * otra forma. Un renombrado guarda el nombre anterior y el nuevo, porque la
 * tabla {@code accounts} solo conserva el último; una corrección guarda el id
 * de su reemplazo; un vínculo guarda de quién era la cuenta antes, porque
 * {@code accounts.person_id} solo conserva de quién es ahora. Montos y
 * descripciones no se copian nunca: ya están en tablas que nadie puede
 * modificar, y duplicarlos sería una segunda verdad que alguien tendría que
 * recordar mantener.
 *
 * <p>Los valores son cadenas y no objetos cualesquiera: el JSON que llega a la
 * base es predecible, pequeño y no arrastra la forma interna de nada.
 */
public record AuditRecord(
        AuditedEntity entity,
        UUID entityId,
        AuditAction action,
        TransactionSource source,
        Map<String, String> before,
        Map<String, String> after) {

    public AuditRecord {
        Objects.requireNonNull(entity, "entity");
        Objects.requireNonNull(entityId, "entityId");
        Objects.requireNonNull(action, "action");
        Objects.requireNonNull(source, "source");
        before = before == null ? Map.of() : Map.copyOf(before);
        after = after == null ? Map.of() : Map.copyOf(after);
    }

    public static AuditRecord movementRecorded(TransactionId id) {
        return ofTransaction(id, AuditAction.CREATE, Map.of(), Map.of());
    }

    public static AuditRecord movementCancelled(TransactionId id) {
        return ofTransaction(id, AuditAction.CANCEL, Map.of(), Map.of());
    }

    /**
     * La corrección se anota sobre la original, que es el id por el que alguien
     * va a preguntar, y deja dicho en {@code after} cuál la reemplazó.
     */
    public static AuditRecord movementAmended(
            TransactionId original, TransactionId replacement) {

        return ofTransaction(original, AuditAction.AMEND,
                Map.of(), Map.of("replacedBy", replacement.toString()));
    }

    public static AuditRecord accountOpened(AccountId id) {
        return ofAccount(id, AuditAction.CREATE, Map.of(), Map.of());
    }

    public static AuditRecord accountArchived(AccountId id) {
        return ofAccount(id, AuditAction.ARCHIVE, Map.of(), Map.of());
    }

    public static AuditRecord accountRenamed(AccountId id, String from, String to) {
        return ofAccount(id, AuditAction.RENAME,
                Map.of("name", from), Map.of("name", to));
    }

    public static AuditRecord personOpened(PersonId id) {
        return ofPerson(id, AuditAction.CREATE, Map.of(), Map.of());
    }

    public static AuditRecord personArchived(PersonId id) {
        return ofPerson(id, AuditAction.ARCHIVE, Map.of(), Map.of());
    }

    public static AuditRecord personRenamed(PersonId id, String from, String to) {
        return ofPerson(id, AuditAction.RENAME,
                Map.of("name", from), Map.of("name", to));
    }

    /**
     * El vínculo se anota sobre la cuenta, que es la fila que cambió y el id por
     * el que alguien va a preguntar.
     *
     * <p>Van los ids y no los nombres: un nombre se puede cambiar después y
     * entonces la bitácora contaría una historia que ya no cuadra con el
     * directorio. Y son ids, no nombres, también por lo de siempre — aquí no se
     * copian datos de la persona.
     *
     * @param before de quién era antes, o {@code null} si no era de nadie
     */
    public static AuditRecord accountLinked(AccountId id, PersonId before, PersonId after) {
        return ofAccount(id, AuditAction.LINK,
                before == null ? Map.of() : Map.of("person", before.toString()),
                Map.of("person", after.toString()));
    }

    public static AuditRecord budgetSet(BudgetId id) {
        return ofBudget(id, AuditAction.CREATE, Map.of(), Map.of());
    }

    /**
     * Cambiar un límite es la misma forma que corregir un movimiento: se anota
     * sobre el nuevo, que es el que queda vivo, y deja dicho en {@code before}
     * cuál reemplazó. Sin ese id, la bitácora diría que apareció un límite de
     * la nada el mismo día en que otro dejó de contar, y habría que adivinar
     * que son el mismo hecho.
     *
     * <p>El monto no va, ni el viejo ni el nuevo: en la bitácora no van montos.
     */
    public static AuditRecord budgetChanged(BudgetId id, BudgetId replaced) {
        return ofBudget(id, AuditAction.AMEND,
                Map.of("budget", replaced.toString()), Map.of());
    }

    private static AuditRecord ofBudget(
            BudgetId id, AuditAction action,
            Map<String, String> before, Map<String, String> after) {

        return new AuditRecord(AuditedEntity.BUDGET, id.value(), action,
                TransactionSource.APP, before, after);
    }

    private static AuditRecord ofTransaction(
            TransactionId id, AuditAction action,
            Map<String, String> before, Map<String, String> after) {

        return new AuditRecord(AuditedEntity.TRANSACTION, id.value(), action,
                TransactionSource.APP, before, after);
    }

    private static AuditRecord ofAccount(
            AccountId id, AuditAction action,
            Map<String, String> before, Map<String, String> after) {

        return new AuditRecord(AuditedEntity.ACCOUNT, id.value(), action,
                TransactionSource.APP, before, after);
    }

    private static AuditRecord ofPerson(
            PersonId id, AuditAction action,
            Map<String, String> before, Map<String, String> after) {

        return new AuditRecord(AuditedEntity.PERSON, id.value(), action,
                TransactionSource.APP, before, after);
    }
}
