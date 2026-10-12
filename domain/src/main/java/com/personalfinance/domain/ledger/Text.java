package com.personalfinance.domain.ledger;

/**
 * The one place that decides what a piece of free text may be.
 *
 * <p>Three fields in this domain come from outside and end up on a screen, in a
 * log line and in a chat message: a transaction's description, the reason of a
 * cancellation and an account's name. All three are trimmed, bounded in length
 * and refuse control characters, and they do it here so the rule cannot drift
 * apart between them.
 *
 * <p>The length is counted in code points, not in {@code char}s, because that
 * is what {@code char_length} counts in Postgres and the CHECK in the V4 has to
 * agree with this. An emoji is one character in both.
 *
 * <p>Control characters are refused rather than stripped, and they are looked
 * for <em>before</em> trimming. Both halves of that matter. Stripping would
 * mean the text that was stored is not the text that arrived; and checking
 * after trimming would let "Almuerzo\n" through without a word, because
 * {@code trim()} eats a newline, a tab and a NUL at either end. Whatever feeds
 * this domain — a chat message, an HTTP body — cleans its own input first.
 */
final class Text {

    /**
     * The field, trimmed, or empty when it was null. Blankness is not decided
     * here: a description may be empty and an account name may not.
     *
     * <p>The control characters are looked for in what arrived, the length in
     * what is left after trimming. That is the only order that holds both
     * promises: nothing with a control character anywhere gets in, and the
     * limit matches the {@code char_length} that the V4 checks on the stored
     * value.
     *
     * @param field the name the exception reports, never the text itself
     */
    static String within(String field, String candidate, int maxCodePoints) {
        String arrived = candidate == null ? "" : candidate;

        if (arrived.codePoints().anyMatch(Character::isISOControl)) {
            throw new ControlCharactersException(field);
        }

        String trimmed = arrived.trim();
        if (trimmed.codePointCount(0, trimmed.length()) > maxCodePoints) {
            throw new TextTooLongException(field, maxCodePoints);
        }
        return trimmed;
    }

    private Text() {
    }
}
