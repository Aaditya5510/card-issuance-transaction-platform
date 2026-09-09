package com.cardplatform.infrastructure.persistence.entity;

import com.cardplatform.common.audit.BaseAuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.math.BigDecimal;

@Entity
@Table(name = "accounts")
public class AccountJpaEntity extends BaseAuditableEntity {

    @Column(name = "account_number", nullable = false, unique = true, length = 32)
    private String accountNumber;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Column(name = "available_balance", nullable = false, precision = 18, scale = 4)
    private BigDecimal availableBalance;

    @Column(name = "pending_hold_balance", nullable = false, precision = 18, scale = 4)
    private BigDecimal pendingHoldBalance;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    public AccountJpaEntity() {
    }

    public String getAccountNumber() {
        return accountNumber;
    }

    public void setAccountNumber(String accountNumber) {
        this.accountNumber = accountNumber;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public BigDecimal getAvailableBalance() {
        return availableBalance;
    }

    public void setAvailableBalance(BigDecimal availableBalance) {
        this.availableBalance = availableBalance;
    }

    public BigDecimal getPendingHoldBalance() {
        return pendingHoldBalance;
    }

    public void setPendingHoldBalance(BigDecimal pendingHoldBalance) {
        this.pendingHoldBalance = pendingHoldBalance;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
