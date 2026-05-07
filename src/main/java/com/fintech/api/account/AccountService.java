package com.fintech.api.account;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/**
 * Camada de serviço para operações de conta.
 *
 * EXAME — @Transactional:
 *  - Gerencia o ciclo de vida da transação (begin → commit/rollback).
 *  - Por padrão: propaga REQUIRED (usa transação existente ou cria nova),
 *    faz rollback apenas em RuntimeException/Error.
 *  - readOnly = true: hint de performance — o JPA pode pular o "dirty checking"
 *    (comparação de estado) ao final da transação, pois nada será salvo.
 *
 * EXAME — @Service:
 *  - Especialização de @Component — semântica: camada de negócio.
 *  - Elegível para ser proxy AOP (ex: @Transactional funciona via proxy).
 *
 * EXAME — Propagation types mais cobrados:
 *  REQUIRED     → padrão; usa ou cria transação
 *  REQUIRES_NEW → sempre cria nova transação (suspende a atual)
 *  SUPPORTS     → usa se existir, senão executa sem transação
 *  NOT_SUPPORTED → sempre executa sem transação
 *  NEVER        → lança exceção se existir transação ativa
 *  MANDATORY    → exige transação ativa, senão lança exceção
 *
 *  * EXAME — Paginação com Spring Data:
 *  *  Pageable encapsula: página, tamanho e ordenação.
 *  *  Page<T> retorna: conteúdo, total de elementos, total de páginas,
 *  *  página atual, se é primeira/última página, etc.
 *  *
 *  *  O cliente controla via query params:
 *  *  GET /accounts?page=0&size=10&sort=ownerName,asc
 *  *
 *  * EXAME — Specification.where():
 *  *  Ponto de entrada seguro para encadeamento.
 *  *  Specification.where(null) retorna Specification que não adiciona WHERE.
 *  *  Depois disso, .and() e .or() combinam os predicados.
 *  */
@Service
@Transactional(readOnly = true)
public class AccountService {

    private final AccountRepository accountRepository;

    // EXAME — Constructor Injection: forma preferida (garante imutabilidade e facilita testes)
    // @Autowired é opcional quando há apenas um construtor (Spring 4.3+)
    public AccountService(AccountRepository accountRepository) {
        this.accountRepository = accountRepository;
    }

    /**
     * Busca com filtros opcionais e paginação.
     * Qualquer combinação de filtros funciona — inclusive nenhum (retorna tudo paginado).
     */
    public Page<Account> search(String ownerName, BigDecimal minBalance,
                                BigDecimal maxBalance, Pageable pageable) {
        Specification<Account> spec = Specification
                .where(AccountSpecifications.hasOwnerName(ownerName))
                .and(AccountSpecifications.hasMinBalance(minBalance))
                .and(AccountSpecifications.hasMaxBalance(maxBalance));

        return accountRepository.findAll(spec, pageable);
    }

    public List<Account> findAll() {
        return accountRepository.findAll();
    }

    public Account findById(Long id) {
        return accountRepository.findById(id)
                .orElseThrow(() -> new AccountNotFoundException(id));
    }

    public Account findByAccountNumber(String accountNumber) {
        return accountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(() -> new AccountNotFoundException(accountNumber));
    }

    // EXAME: @Transactional sem readOnly → sobrescreve o da classe, agora permite escrita
    @Transactional
    public Account create(String ownerName, String accountNumber, BigDecimal initialBalance) {
        if (accountRepository.existsByAccountNumber(accountNumber)) {
            throw new DuplicateAccountException(accountNumber);
        }
        return accountRepository.save(new Account(ownerName, accountNumber, initialBalance));
    }
}
