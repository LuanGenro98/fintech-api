package com.fintech.api.transfer;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Entidade que representa uma transferência entre contas.
 *
 * EXAME — note o campo `idempotencyKey`:
 * Futuramente usaremos AOP para garantir que a mesma transferência
 * não seja processada duas vezes (mesmo se o cliente reenviar a requisição).
 */
@Entity
@Table(name = "transfers", indexes = {
        // Índice no idempotencyKey para busca eficiente
        @Index(name = "idx_transfers_idempotency_key", columnList = "idempotency_key", unique = true)
})
public class Transfer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Chave de idempotência: o cliente gera e envia um UUID único por requisição.
     * Se a mesma key chegar novamente, retornamos o resultado original sem re-processar.
     * Isso protege contra duplicidade em caso de retry de rede.
     */
    @Column(name = "idempotency_key", nullable = false, unique = true, length = 36)
    private String idempotencyKey;

    @Column(nullable = false, length = 20)
    private String sourceAccountNumber;

    @Column(nullable = false, length = 20)
    private String destinationAccountNumber;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TransferStatus status;

    private String failureReason;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    protected Transfer() {}

    public Transfer(String idempotencyKey, String source, String destination, BigDecimal amount) {
        this.idempotencyKey = idempotencyKey;
        this.sourceAccountNumber = source;
        this.destinationAccountNumber = destination;
        this.amount = amount;
        this.status = TransferStatus.PENDING;
    }

    public void complete()                    { this.status = TransferStatus.COMPLETED; }
    public void fail(String reason)           { this.status = TransferStatus.FAILED; this.failureReason = reason; }
    public boolean isCompleted()              { return this.status == TransferStatus.COMPLETED; }

    public Long getId()                       { return id; }
    public String getIdempotencyKey()         { return idempotencyKey; }
    public String getSourceAccountNumber()    { return sourceAccountNumber; }
    public String getDestinationAccountNumber(){ return destinationAccountNumber; }
    public BigDecimal getAmount()             { return amount; }
    public TransferStatus getStatus()         { return status; }
    public String getFailureReason()          { return failureReason; }
    public LocalDateTime getCreatedAt()       { return createdAt; }
    public LocalDateTime getUpdatedAt()       { return updatedAt; }

    public enum TransferStatus {
        PENDING, COMPLETED, FAILED
    }
}
