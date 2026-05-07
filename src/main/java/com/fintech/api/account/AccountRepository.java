package com.fintech.api.account;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;

/**
 * Repositório Spring Data JPA para Account.
 *
 * EXAME — Spring Data JPA:
 *  - JpaRepository já fornece: save, findById, findAll, delete, count, existsById...
 *  - Derived Query Methods: o Spring interpreta o nome do método e gera o SQL automaticamente.
 *    Ex: findByAccountNumber → SELECT * FROM accounts WHERE account_number = ?
 *  - @Query: JPQL ou SQL nativo (nativeQuery = true) para queries customizadas.
 *  - @Lock: aplica LockModeType na query — usado para Pessimistic Locking.
 *
 * EXAME: Diferença entre PESSIMISTIC_WRITE (SELECT FOR UPDATE) e OPTIMISTIC (@Version).
 *
 * EXAME — JpaSpecificationExecutor:
 *  Adiciona suporte a queries dinâmicas via Specification (Criteria API).
 *  Métodos adicionados:
 *   - findAll(Specification<T>)
 *   - findAll(Specification<T>, Pageable)
 *   - findAll(Specification<T>, Sort)
 *   - count(Specification<T>)
 *   - exists(Specification<T>)
 * EXAME — Projections no repositório:
 *
 * Interface-based → só muda o tipo de retorno, Spring cuida do resto.
 * Class-based     → precisa de @Query com constructor expression JPQL.
 * Dynamic         → tipo genérico <T>, caller decide qual Projection usar.
 *
 * EXAME — Dynamic Projection:
 *  Um único método serve múltiplos tipos de retorno.
 *  findBy...(Class<T> type) → Spring adapta a query ao tipo solicitado.
 *  Muito útil quando o mesmo dado é consumido de formas diferentes.
 */
public interface AccountRepository extends JpaRepository<Account, Long>,
                                           JpaSpecificationExecutor<Account> {

    // Derived Query Method — Spring gera o SQL pelo nome do método
    Optional<Account> findByAccountNumber(String accountNumber);

    // Pessimistic Locking: garante que nenhuma outra transação leia/escreva
    // esta linha enquanto nossa transação estiver ativa.
    // Usado quando precisamos de consistência absoluta (ex: em transferências críticas).
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM Account a WHERE a.accountNumber = :accountNumber")
    Optional<Account> findByAccountNumberForUpdate(@Param("accountNumber") String accountNumber);

    boolean existsByAccountNumber(String accountNumber);

    // ── Projections ──────────────────────────────────────────────────────────

    /**
     * TIPO 1 — Interface-based Projection (Closed):
     * Spring gera: SELECT a.id, a.owner_name, a.account_number FROM accounts
     * Apenas as colunas dos getters da interface são buscadas.
     */
    List<AccountSummary> findAllProjectedBy();

    /**
     * TIPO 2 — Class-based Projection:
     * Constructor expression JPQL: NEW FullClassName(campos...)
     * A ordem dos args DEVE bater com o construtor do record/classe.
     *
     * EXAME — JPQL vs SQL nativo:
     *  JPQL usa nomes de campos Java (ownerName, balance)
     *  SQL nativo usa nomes de colunas (owner_name, balance)
     */
    @Query("SELECT new com.fintech.api.account.AccountBalanceView" +
           "(a.ownerName, a.balance) FROM Account a")
    List<AccountBalanceView> findAllBalanceViews();

    /**
     * TIPO 3 — Dynamic Projection:
     * O tipo de retorno é genérico — o caller passa a classe desejada.
     *
     * Uso:
     *   repo.findByAccountNumber("ACC-001", AccountSummary.class)   → interface projection
     *   repo.findByAccountNumber("ACC-001", Account.class)          → entidade completa
     *   repo.findByAccountNumber("ACC-001", AccountBalanceView.class) → DTO projection
     */
    <T> Optional<T> findByAccountNumber(String accountNumber, Class<T> type);
}
