package com.fintech.api.account;

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
 */
@Service
@Transactional(readOnly = true)  // padrão read-only para todos os métodos
public class AccountService {

    private final AccountRepository accountRepository;

    // EXAME — Constructor Injection: forma preferida (garante imutabilidade e facilita testes)
    // @Autowired é opcional quando há apenas um construtor (Spring 4.3+)
    public AccountService(AccountRepository accountRepository) {
        this.accountRepository = accountRepository;
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
        Account account = new Account(ownerName, accountNumber, initialBalance);
        return accountRepository.save(account);
        // O JPA faz o INSERT no commit da transação (ao sair do método)
    }
}
