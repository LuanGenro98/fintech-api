package com.fintech.api.account;

import java.math.BigDecimal;

// ─────────────────────────────────────────────────────────────────────────
// TIPO 2 — Class-based Projection (DTO Projection)
// ─────────────────────────────────────────────────────────────────────────
// Record (ou classe) com construtor que o Spring chama via JPQL constructor expression.
// Permite lógica no construtor e métodos customizados.
// Spring gera: SELECT new com.fintech...AccountBalanceDTO(a.ownerName, a.balance) FROM Account a
//
// EXAME — Diferença para Interface-based:
//  Interface → Spring cria proxy, simples mas sem lógica
//  Class     → construtor chamado pelo JPA, permite transformações
//
// EXAME — O construtor DEVE receber os campos na mesma ordem do @Query.
public record AccountBalanceView(
        String ownerName,
        BigDecimal balance,
        String balanceCategory
) {
    public AccountBalanceView(String ownerName, BigDecimal balance) {
        this(ownerName, balance, categorize(balance));
    }

    private static String categorize(BigDecimal balance) {
        if (balance.compareTo(new BigDecimal("5000")) >= 0) return "HIGH";
        if (balance.compareTo(new BigDecimal("1000")) >= 0) return "MEDIUM";
        return "LOW";
    }
}
