package com.fintech.api.account;

import org.springframework.beans.factory.annotation.Value;

import java.math.BigDecimal;

/**
 * Projections para Account.
 *
 * EXAME — O que é uma Projection:
 *  Mecanismo do Spring Data para buscar apenas um subconjunto de campos
 *  de uma entidade. O JPA gera um SELECT apenas com as colunas necessárias.
 *
 * ─────────────────────────────────────────────────────────────────────────────
 * TIPO 1 — Interface-based Projection (Closed)
 * ─────────────────────────────────────────────────────────────────────────────
 * Spring implementa a interface automaticamente em runtime (proxy).
 * Cada getter corresponde a um campo da entidade.
 * "Closed" = Spring sabe exatamente quais colunas buscar → query otimizada.
 *
 * EXAME — PEGADINHA:
 *  O nome do getter DEVE corresponder ao nome do campo na entidade.
 *  getOwnerName() → campo ownerName ✅
 *  getNome()      → campo não existe → Spring lança exceção ❌
 */
public interface AccountSummary{

    Long getId();
    String getOwnerName();
    String getAccountNumber();

    /**
     * TIPO 1.5 — Interface-based Projection (Open) com @Value:
     *
     * Permite campos CALCULADOS via SpEL (Spring Expression Language).
     * "Open" = Spring não pode otimizar a query (precisa de todos os campos
     * para avaliar a expressão) — use com moderação.
     *
     * EXAME — SpEL em Projections:
     *  #{target.campo}              → acessa campo da entidade
     *  #{target.nome + ' ' + target.sobrenome} → concatenação
     *  #{target.balance * 0.1}      → cálculo
     *
     * Aqui retornamos "Alice Silva (ACC-001)" — útil para dropdowns.
     */
    @Value("#{target.ownerName + ' (' + target.accountNumber + ')'}")
    String getDisplayName();
}
