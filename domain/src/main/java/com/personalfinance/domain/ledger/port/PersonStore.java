package com.personalfinance.domain.ledger.port;

import com.personalfinance.domain.ledger.Person;
import com.personalfinance.domain.ledger.PersonId;
import java.time.Instant;
import java.util.List;

/**
 * Where people are kept between runs.
 *
 * <p>People are never deleted, so there is no {@code remove}. The two things
 * that change after a person exists are their name and whether they are
 * archived; which accounts are theirs is kept on the account, which is where
 * {@link AccountStore#linkToPerson} writes it.
 *
 * <p>The store enforces the same rules as the {@link
 * com.personalfinance.domain.ledger.Directory}, with the directory's own
 * exceptions: writing a person twice and archiving twice both fail. The
 * directory in memory and the one in storage can drift apart — another device,
 * a retried request — so neither may assume the other already checked.
 */
public interface PersonStore {

    /**
     * @throws com.personalfinance.domain.ledger.DuplicatePersonException
     *         if that id is already stored
     */
    void open(Person person);

    void rename(PersonId id, String newName);

    /**
     * @throws com.personalfinance.domain.ledger.PersonAlreadyArchivedException
     *         if they were archived before; the first date is what stays
     */
    void markArchived(PersonId id, Instant at);

    /** Everyone ever written down, archived included, oldest first. */
    List<Person> all();
}
