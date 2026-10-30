package com.personalfinance.domain.ledger;

/**
 * Which side of the double entry an account naturally sits on.
 *
 * <p>This is the single place where the sign convention lives. Nothing else in
 * the domain should ever multiply a balance by -1 by hand.
 */
public enum BalanceNature {

    /** Balance grows with positive entries: assets, receivables, expenses. */
    DEBIT(1),

    /** Balance grows with negative entries: payables, third-party funds, income, equity. */
    CREDIT(-1);

    private final int presentationSign;

    BalanceNature(int presentationSign) {
        this.presentationSign = presentationSign;
    }

    /**
     * Turns an internal ledger balance into the number the person expects to see.
     * A third-party fund holding 70,000 has an internal balance of -70,000.
     */
    public int presentationSign() {
        return presentationSign;
    }
}
