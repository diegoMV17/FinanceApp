package com.personalfinance.domain.ledger.port;

import com.personalfinance.domain.ledger.Account;
import com.personalfinance.domain.ledger.AccountId;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Where accounts are kept between runs.
 *
 * <p>Accounts are never deleted, so there is no {@code remove}. The two things
 * that change after an account exists are its name and whether it is archived.
 */
public interface AccountStore {

    void open(Account account);

    void markArchived(AccountId id, Instant at);

    void rename(AccountId id, String newName);

    Optional<Account> find(AccountId id);

    /** Everything ever opened, archived included, oldest first. */
    List<Account> all();
}
