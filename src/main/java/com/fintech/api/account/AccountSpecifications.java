package com.fintech.api.account;

import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;

/**
 * Specifications para Account — filtros dinâmicos e combináveis.
 *
 * EXAME — O que é uma Specification:
 *  É uma implementação do padrão Specification (DDD) integrada à JPA Criteria API.
 *  Cada Specification é uma função que recebe:
 *   - root       → a entidade raiz da query (acesso aos campos)
 *   - query      → a query em construção (útil para subqueries, distinct)
 *   - cb         → CriteriaBuilder — fábrica de predicados (like, gt, lt, and, or...)
 *  E retorna um Predicate (cláusula WHERE) ou null (ignora o critério).
 *
 * EXAME — Por que uma classe utilitária estática?
 *  Specifications são combinadas externamente (no Service).
 *  Mantê-las em uma classe separada segue o princípio da responsabilidade única
 *  e facilita reuso entre diferentes queries.
 *
 * EXAME — Specification.where(null) é seguro:
 *  Spring Data trata null como "sem restrição" — não adiciona WHERE clause.
 *  Isso permite encadear .and() e .or() sem verificar nulos manualmente.
 */
public class AccountSpecifications {

    private AccountSpecifications() {} // utilitária, não instanciar

    /**
     * Filtra por nome do dono (case-insensitive, busca parcial).
     * Retorna null se o filtro não foi fornecido — Spring ignora o critério.
     */
    public static Specification<Account> hasOwnerName(String ownerName) {
        return (root, query, cb) -> {
            if (ownerName == null || ownerName.isBlank()) {
                return null; // critério ignorado
            }
            // cb.lower → SQL: LOWER(owner_name)
            // cb.like  → SQL: LIKE '%alice%'
            return cb.like(
                cb.lower(root.get("ownerName")),
                "%" + ownerName.toLowerCase() + "%"
            );
        };
    }

    /**
     * Filtra por saldo mínimo (balance >= minBalance).
     *
     * EXAME — cb.greaterThanOrEqualTo → SQL: balance >= ?
     * Outros predicados comuns:
     *   cb.equal, cb.notEqual
     *   cb.greaterThan, cb.lessThan
     *   cb.between
     *   cb.isNull, cb.isNotNull
     *   cb.in(root.get("status"), List.of(...))
     */
    public static Specification<Account> hasMinBalance(BigDecimal minBalance) {
        return (root, query, cb) -> {
            if (minBalance == null) {
                return null;
            }
            return cb.greaterThanOrEqualTo(root.get("balance"), minBalance);
        };
    }

    /**
     * Filtra por saldo máximo (balance <= maxBalance).
     */
    public static Specification<Account> hasMaxBalance(BigDecimal maxBalance) {
        return (root, query, cb) -> {
            if (maxBalance == null) {
                return null;
            }
            return cb.lessThanOrEqualTo(root.get("balance"), maxBalance);
        };
    }
}
