package com.fintech.api.account;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Entidade JPA que representa uma conta bancária.
 *
 * EXAME — Anotações JPA importantes:
 *  - @Entity       → mapeia a classe para uma tabela no banco
 *  - @Table        → personaliza o nome da tabela (opcional; padrão = nome da classe)
 *  - @Id           → chave primária
 *  - @GeneratedValue → estratégia de geração do ID
 *  - @Column       → personaliza coluna (nullable, unique, length...)
 *  - @Version      → controle de concorrência otimista (Optimistic Locking)
 *
 * EXAME — Bean Validation:
 *  O Spring integra automaticamente com Hibernate Validator.
 *  @NotBlank, @PositiveOrZero etc. são validados quando @Valid/@Validated é usado.
 */
@Entity
@Table(name = "accounts")
public class Account {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "Owner name is required")
    @Column(nullable = false)
    private String ownerName;

    @Column(nullable = false, unique = true, length = 20)
    private String accountNumber;

    @PositiveOrZero(message = "Balance cannot be negative")
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal balance;

    /**
     * @Version habilita Optimistic Locking:
     * O JPA incrementa este campo a cada UPDATE.
     * Se duas transações tentam atualizar a mesma versão, a segunda lança
     * OptimisticLockException — evitando saldo corrompido em concorrência.
     *
     * EXAME: Diferença entre Optimistic (version field) e Pessimistic (SELECT FOR UPDATE).
     */
    @Version
    private Long version;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    // ── Construtores ────────────────────────────────────────────────────────────

    protected Account() {
        // JPA exige construtor sem args (pode ser protected)
    }

    public Account(String ownerName, String accountNumber, BigDecimal initialBalance) {
        this.ownerName = ownerName;
        this.accountNumber = accountNumber;
        this.balance = initialBalance;
    }

    // ── Métodos de domínio ───────────────────────────────────────────────────────

    public void debit(BigDecimal amount) {
        if (this.balance.compareTo(amount) < 0) {
            throw new InsufficientFundsException(this.accountNumber, this.balance, amount);
        }
        this.balance = this.balance.subtract(amount);
    }

    public void credit(BigDecimal amount) {
        this.balance = this.balance.add(amount);
    }

    // ── Getters ──────────────────────────────────────────────────────────────────

    public Long getId()                  { return id; }
    public String getOwnerName()         { return ownerName; }
    public String getAccountNumber()     { return accountNumber; }
    public BigDecimal getBalance()       { return balance; }
    public Long getVersion()             { return version; }
    public LocalDateTime getCreatedAt()  { return createdAt; }
}
