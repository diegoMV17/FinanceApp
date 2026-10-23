package com.personalfinance.domain.ledger;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * The catalogue of accounts: your banks, the people you owe or who owe you, the
 * funds you hold for someone else, and your categories (HU08, HU11).
 *
 * <p>This is the only place that hands out {@link Account} instances. That
 * matters more than it looks: two {@code Account} objects called "Bancolombia"
 * with different ids are two different accounts to the ledger, and the balance
 * would quietly split in half. One catalogue, one instance per account.
 *
 * <p>Nothing is ever removed. Archiving takes an account out of the lists you
 * pick from, and leaves it in the ones you read history from.
 */
public final class Chart {

    private final Map<AccountId, Account> accounts = new LinkedHashMap<>();

    /**
     * Opens a new account under the id the caller brought, which is how the
     * phone can create an account offline and send it afterwards.
     */
    public Account open(AccountId id, AccountType type, String name) {
        return add(Account.open(id, type, name));
    }

    /** Opens a new account with an id of its own. The usual way to create one. */
    public Account open(AccountType type, String name) {
        return open(AccountId.generate(), type, name);
    }

    /**
     * Registers an account built elsewhere, such as one loaded from the
     * database.
     *
     * <p>An id already in the catalogue is refused rather than replaced: the
     * entries already written point at the instance that is there, and swapping
     * it would split that account's balance in two.
     */
    public Account add(Account account) {
        Objects.requireNonNull(account, "account");
        if (accounts.containsKey(account.id())) {
            throw new DuplicateAccountException(account.id());
        }
        if (!account.isArchived()) {
            refuseDuplicateName(account.type(), account.name());
        }
        accounts.put(account.id(), account);
        return account;
    }

    /**
     * HU19. Says that this account is one of somebody's.
     *
     * <p>It goes through here and not through the account because the chart is
     * the only thing that hands out accounts, and therefore the only thing that
     * may change one. The person has to be an instance the {@link Directory}
     * handed out; a copy would split that person in two.
     */
    public void linkToPerson(AccountId id, Person person) {
        require(id).linkTo(person);
    }

    public void rename(AccountId id, String newName) {
        Account account = require(id);
        if (!account.answersTo(newName)) {
            refuseDuplicateName(account.type(), newName);
        }
        account.rename(newName);
    }

    /**
     * HU11, and the rules decided for HU08: a category archives with its history,
     * an account that still holds money does not archive at all.
     *
     * <p>The ledger is asked rather than told. An account cannot be trusted to
     * report its own balance, and a caller cannot be trusted to supply one, so
     * the only balance that counts here is the one the book computes.
     *
     * <p>What it checks is the balance once <em>every</em> recorded movement has
     * taken effect, scheduled ones included. An account that reads zero today but
     * has next month's rent pointing at it is not finished with.
     */
    public Account archive(AccountId id, Ledger ledger, Instant when) {
        Objects.requireNonNull(ledger, "ledger");
        Account account = require(id);
        account.archive(ledger.eventualBalanceOf(account), when);
        return account;
    }

    public Account require(AccountId id) {
        Account found = accounts.get(Objects.requireNonNull(id, "id"));
        if (found == null) {
            throw new UnknownAccountException(id);
        }
        return found;
    }

    public Optional<Account> find(AccountId id) {
        return Optional.ofNullable(accounts.get(Objects.requireNonNull(id, "id")));
    }

    /** Everything ever opened, archived included. For history and reports. */
    public List<Account> all() {
        return List.copyOf(accounts.values());
    }

    /** Everything of one kind, archived included. */
    public List<Account> allOf(AccountType type) {
        Objects.requireNonNull(type, "type");
        return accounts.values().stream()
                .filter(account -> account.type() == type)
                .toList();
    }

    /** Everything of one person, archived included. The three sides of HU19. */
    public List<Account> allOf(Person person) {
        Objects.requireNonNull(person, "person");
        return accounts.values().stream()
                .filter(account -> account.belongsTo(person))
                .toList();
    }

    /** What is still open in one person's name; it is what decides if they can be archived. */
    public List<Account> selectableOf(Person person) {
        return allOf(person).stream().filter(account -> !account.isArchived()).toList();
    }

    /** What the person can still pick when recording a new movement (HU12). */
    public List<Account> selectable() {
        return accounts.values().stream().filter(account -> !account.isArchived()).toList();
    }

    /** What the person can still pick, of one kind: the selector behind each field. */
    public List<Account> selectableOf(AccountType type) {
        Objects.requireNonNull(type, "type");
        return selectable().stream().filter(account -> account.type() == type).toList();
    }

    public List<Account> archived() {
        return accounts.values().stream().filter(Account::isArchived).toList();
    }

    private void refuseDuplicateName(AccountType type, String name) {
        boolean taken = selectableOf(type).stream().anyMatch(account -> account.answersTo(name));
        if (taken) {
            throw new DuplicateAccountNameException(type, name.trim());
        }
    }
}
