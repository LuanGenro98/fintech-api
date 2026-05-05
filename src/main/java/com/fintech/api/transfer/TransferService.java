package com.fintech.api.transfer;

import com.fintech.api.account.Account;
import com.fintech.api.account.AccountRepository;
import com.fintech.api.account.AccountNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/**
 * Serviço de transferência — coração da lógica de negócio.
 *
 * EXAME — @Transactional em profundidade:
 *
 * 1. COMO FUNCIONA:
 *    O Spring cria um PROXY (via CGLIB por padrão) em volta do bean.
 *    Quando você chama transfer(), o proxy intercepta, abre transação,
 *    executa o método, e faz commit (ou rollback se exceção).
 *
 * 2. ARMADILHA CLÁSSICA — Self-invocation:
 *    Se um método @Transactional chama OUTRO método @Transactional
 *    da MESMA classe diretamente (sem passar pelo proxy), a anotação
 *    do segundo método É IGNORADA. Sempre injete a própria classe
 *    ou extraia para outro bean se precisar de transações aninhadas.
 *
 * 3. ROLLBACK:
 *    Por padrão, rollback apenas em RuntimeException e Error.
 *    Para checked exceptions: @Transactional(rollbackFor = MinhaException.class)
 *
 * 4. A transferência usa Pessimistic Locking (SELECT FOR UPDATE)
 *    para evitar race condition nos saldos.
 */
@Service
@Transactional(readOnly = true)
public class TransferService {

    private final TransferRepository transferRepository;
    private final AccountRepository accountRepository;

    public TransferService(TransferRepository transferRepository,
                           AccountRepository accountRepository) {
        this.transferRepository = transferRepository;
        this.accountRepository = accountRepository;
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
     * Executa uma transferência de forma atômica.
     *
     * A anotação @Transactional garante que:
     * - O débito e o crédito ocorrem juntos (atomicidade)
     * - Se qualquer exceção ocorrer, AMBAS as operações são revertidas
     * - O estado do banco nunca fica "no meio do caminho"
     *
     * EXAME: Isso é o "A" do ACID — Atomicity.
     */
    @Transactional
    public Transfer execute(String idempotencyKey, String sourceNumber,
                            String destinationNumber, BigDecimal amount) {

        // Cria o registro da transferência (status: PENDING)
        Transfer transfer = new Transfer(idempotencyKey, sourceNumber, destinationNumber, amount);
        transfer = transferRepository.save(transfer);

        try {
            // Busca as contas COM LOCK (Pessimistic Locking)
            // Nenhuma outra transação pode modificar essas linhas até o commit
            Account source = accountRepository.findByAccountNumberForUpdate(sourceNumber)
                    .orElseThrow(() -> new AccountNotFoundException(sourceNumber));

            Account destination = accountRepository.findByAccountNumberForUpdate(destinationNumber)
                    .orElseThrow(() -> new AccountNotFoundException(destinationNumber));

            // Regras de negócio — podem lançar InsufficientFundsException
            source.debit(amount);
            destination.credit(amount);

            // O dirty checking do JPA detecta as mudanças nas entidades
            // e executa os UPDATEs automaticamente no commit
            accountRepository.save(source);
            accountRepository.save(destination);

            transfer.complete();
            return transferRepository.save(transfer);

        } catch (Exception e) {
            // Salva o failure — mas atenção: se a transação fizer rollback,
            // este save também será revertido! 
            // Na sessão de AOP vamos resolver isso com REQUIRES_NEW.
            transfer.fail(e.getMessage());
            transferRepository.save(transfer);
            throw e; // Re-lança para o Spring fazer rollback da transação inteira
        }
    }
}
