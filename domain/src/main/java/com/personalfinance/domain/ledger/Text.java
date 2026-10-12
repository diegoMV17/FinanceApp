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
 * <p>Control characters are refused rather than stripped. Silently removing
 * them would mean the text that was stored is not the text that was sent, and
 * what arrived was not what the person wrote either way.
 */
final class Text {

    /**
     * The field, trimmed, or empty when it was null. Blankness is not decided
     * here: a description may be empty and an account name may not.
     *
     * @param field the name the exception reports, never the text itself
     */
    static String within(String field, String candidate, int maxCodePoints) {
        String trimmed = candidate == null ? "" : candidate.trim();

        if (trimmed.codePointCount(0, trimmed.length()) > maxCodePoints) {
            throw new TextTooLongException(field, maxCodePoints);
        }
        if (trimmed.codePoints().anyMatch(Character::isISOControl)) {
            throw new ControlCharactersException(field);
        }
        return trimmed;
    }

    private Text() {
    }
}
