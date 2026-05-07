package com.fintech.api.account;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;
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
}
