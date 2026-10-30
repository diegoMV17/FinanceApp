package com.personalfinance.domain.ledger.port;

import com.personalfinance.domain.ledger.Account;
import com.personalfinance.domain.ledger.AccountId;
import com.personalfinance.domain.ledger.Directory;
import com.personalfinance.domain.ledger.PersonId;
import java.time.Instant;
import java.util.List;

/**
 * Where accounts are kept between runs.
 *
 * <p>Accounts are never deleted, so there is no {@code remove}. The three
 * things that change after an account exists are its name, whether it is
 * archived, and whose it is.
 *
 * <p>Reading takes the {@link Directory} because an account stores only the id
 * of its person. Resolving that id against the one directory is what guarantees
 * a loaded account points at the same {@code Person} instance as everything
 * else; a store that built its own people would split a position in two. It is
 * the same reason {@link TransactionStore} reads with the chart.
 */
public interface AccountStore {

    void open(Account account);

    void markArchived(AccountId id, Instant at);

    void rename(AccountId id, String newName);

    /**
     * HU19. Says whose account this is. Moving the link to somebody else is
     * allowed; clearing it is not offered, here or in the domain, and the
     * trigger of the V6 refuses it from outside the application too.
     */
    void linkToPerson(AccountId id, PersonId person);

    /** Everything ever opened, archived included, oldest first. */
    List<Account> all(Directory directory);
}
