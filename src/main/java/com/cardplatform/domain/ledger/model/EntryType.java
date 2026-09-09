package com.cardplatform.domain.ledger.model;

/**
 * Legacy entry type alias for PostingType.
 */
public enum EntryType {
    DEBIT,
    CREDIT;

    public PostingType toPostingType() {
        return PostingType.valueOf(this.name());
    }

    public static EntryType fromPostingType(PostingType postingType) {
        return EntryType.valueOf(postingType.name());
    }
}
