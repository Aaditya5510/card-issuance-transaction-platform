package com.cardplatform.domain.ledger.model;

import com.cardplatform.common.money.MonetaryAmount;

/**
 * Standard FinTech 5-Category Chart of Accounts Classification.
 * Encapsulates standard accounting normal balance directions (Debits vs Credits).
 */
public enum AccountType {

    /**
     * Assets (e.g. Cash, Settlement Receivables, Central Bank reserves).
     * Normal balance: DEBIT. Debits increase balance, Credits decrease balance.
     */
    ASSET(true),

    /**
     * Liabilities (e.g. Cardholder Deposit Balances, Merchant Payables).
     * Normal balance: CREDIT. Credits increase balance, Debits decrease balance.
     */
    LIABILITY(false),

    /**
     * Equity (e.g. Retained Earnings, Shareholder Capital).
     * Normal balance: CREDIT. Credits increase balance, Debits decrease balance.
     */
    EQUITY(false),

    /**
     * Revenue / Income (e.g. Interchange Fee Revenue, FX Markup).
     * Normal balance: CREDIT. Credits increase balance, Debits decrease balance.
     */
    REVENUE(false),

    /**
     * Expenses (e.g. Network Assessment Fees, Scheme Costs, Chargeback Losses).
     * Normal balance: DEBIT. Debits increase balance, Credits decrease balance.
     */
    EXPENSE(true);

    private final boolean debitNormal;

    AccountType(boolean debitNormal) {
        this.debitNormal = debitNormal;
    }

    public boolean isDebitNormal() {
        return debitNormal;
    }

    public boolean isCreditNormal() {
        return !debitNormal;
    }

    /**
     * Calculates the signed net balance given total accumulated debits and credits according to normal balance rules.
     */
    public MonetaryAmount calculateNetBalance(MonetaryAmount totalDebits, MonetaryAmount totalCredits) {
        if (debitNormal) {
            return totalDebits.subtract(totalCredits);
        } else {
            return totalCredits.subtract(totalDebits);
        }
    }
}
