package com.fintech.api.transfer;

import com.fintech.api.account.Account;
import com.fintech.api.account.AccountRepository;
import com.fintech.api.account.AccountNotFoundException;
import com.fintech.api.shared.aop.Idempotent;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Service
@Transactional(readOnly = true)
public class TransferService {

    private final TransferRepository transferRepository;
    private final AccountRepository accountRepository;
    private final TransferAuditService auditService;

    public TransferService(TransferRepository transferRepository,
                           AccountRepository accountRepository,
                           TransferAuditService auditService) {
        this.transferRepository = transferRepository;
        this.accountRepository = accountRepository;
        this.auditService = auditService;
    }

    public List<Transfer> findAll() {
        return transferRepository.findAll();
    }

    public Transfer findById(Long id) {
        return transferRepository.findById(id)
                .orElseThrow(() -> new TransferNotFoundException(id));
    }

    public Optional<Transfer> findByIdempotencyKey(String key) {
        return transferRepository.findByIdempotencyKey(key);
    }

    /**
     * CORREÇÃO DO DEADLOCK — Por que funcionava errado antes:
     *
     * O problema era que salvávamos o Transfer como PENDING dentro da Tx A,
     * então o objeto já tinha ID. Quando o REQUIRES_NEW (Tx B) tentava fazer
     * save() nesse mesmo objeto, o JPA executava um MERGE (UPDATE), que tentava
     * modificar uma linha que a Tx A ainda segurava com lock → deadlock no H2.
     *
     * A correção: Tx A NÃO salva o Transfer. Ela cria o objeto transiente
     * (sem ID) e repassa ao auditService, que faz o INSERT dentro do REQUIRES_NEW.
     * Como a Tx A nunca tocou na tabela TRANSFERS, não há conflito de lock.
     *
     * LIÇÃO EXAME:
     * REQUIRES_NEW cria uma transação independente, mas ela ainda compete
     * pelos mesmos recursos do banco. Se a Tx externa segura um lock numa
     * linha que a Tx interna precisa acessar → deadlock garantido.
     * Passe apenas objetos transientes (sem ID) para métodos REQUIRES_NEW,
     * ou use IDs e releia o objeto dentro da nova transação.
     */
    @Idempotent(keyArgumentIndex = 0)
    @Transactional
    public Transfer execute(String idempotencyKey, String sourceNumber,
                            String destinationNumber, BigDecimal amount) {

        // Cria o objeto mas NÃO salva — ainda transiente (sem ID)
        // Quem vai fazer o INSERT é o auditService, dentro do REQUIRES_NEW
        Transfer transfer = new Transfer(idempotencyKey, sourceNumber, destinationNumber, amount);

        try {
            Account source = accountRepository.findByAccountNumberForUpdate(sourceNumber)
                    .orElseThrow(() -> new AccountNotFoundException(sourceNumber));

            Account destination = accountRepository.findByAccountNumberForUpdate(destinationNumber)
                    .orElseThrow(() -> new AccountNotFoundException(destinationNumber));

            source.debit(amount);
            destination.credit(amount);

            accountRepository.save(source);
            accountRepository.save(destination);

            // INSERT como COMPLETED em REQUIRES_NEW — objeto transiente, sem conflito
            return auditService.auditSuccess(transfer);

        } catch (Exception e) {
            // INSERT como FAILED em REQUIRES_NEW — objeto transiente, sem conflito
            // Tx A só tocou em ACCOUNTS, não em TRANSFERS → zero conflito de lock
            auditService.auditFailure(transfer, e.getMessage());
            throw e;
        }
    }
}
