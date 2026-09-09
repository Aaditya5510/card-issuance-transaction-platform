package com.cardplatform.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "card_controls")
@EntityListeners(AuditingEntityListener.class)
public class CardControlsJpaEntity implements org.springframework.data.domain.Persistable<UUID> {

    @Id
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @jakarta.persistence.Transient
    private boolean isNew = true;

    @Override
    public boolean isNew() {
        return isNew;
    }

    @jakarta.persistence.PostLoad
    @jakarta.persistence.PostPersist
    void markNotNew() {
        this.isNew = false;
    }

    @jakarta.persistence.PrePersist
    public void prePersist() {
        if (this.id == null) {
            this.id = UUID.randomUUID();
        }
    }

    @Column(name = "card_id", nullable = false, unique = true)
    private UUID cardId;

    @Column(name = "daily_limit", nullable = false, precision = 18, scale = 4)
    private BigDecimal dailyLimit;

    @Column(name = "per_tx_limit", nullable = false, precision = 18, scale = 4)
    private BigDecimal perTxLimit;

    @Column(name = "online_enabled", nullable = false)
    private Boolean onlineEnabled;

    @Column(name = "atm_enabled", nullable = false)
    private Boolean atmEnabled;

    @Column(name = "international_enabled", nullable = false)
    private Boolean internationalEnabled;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public CardControlsJpaEntity() {
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getCardId() {
        return cardId;
    }

    public void setCardId(UUID cardId) {
        this.cardId = cardId;
    }

    public BigDecimal getDailyLimit() {
        return dailyLimit;
    }

    public void setDailyLimit(BigDecimal dailyLimit) {
        this.dailyLimit = dailyLimit;
    }

    public BigDecimal getPerTxLimit() {
        return perTxLimit;
    }

    public void setPerTxLimit(BigDecimal perTxLimit) {
        this.perTxLimit = perTxLimit;
    }

    public Boolean getOnlineEnabled() {
        return onlineEnabled;
    }

    public void setOnlineEnabled(Boolean onlineEnabled) {
        this.onlineEnabled = onlineEnabled;
    }

    public Boolean getAtmEnabled() {
        return atmEnabled;
    }

    public void setAtmEnabled(Boolean atmEnabled) {
        this.atmEnabled = atmEnabled;
    }

    public Boolean getInternationalEnabled() {
        return internationalEnabled;
    }

    public void setInternationalEnabled(Boolean internationalEnabled) {
        this.internationalEnabled = internationalEnabled;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
