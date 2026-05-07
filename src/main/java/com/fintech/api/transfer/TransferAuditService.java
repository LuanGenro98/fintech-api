package com.fintech.api.transfer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Serviço de auditoria de transferências.
 *
 * EXAME — REQUIRES_NEW funciona corretamente aqui porque:
 * - O objeto Transfer recebido é TRANSIENTE (sem ID, nunca salvo)
 * - A Tx externa (TransferService.execute) só tocou na tabela ACCOUNTS
 * - Portanto, REQUIRES_NEW faz um INSERT limpo em TRANSFERS sem competir
 *   por nenhum lock que a Tx externa esteja segurando
 *
 * REGRA PRÁTICA para REQUIRES_NEW:
 * Nunca passe uma entidade já persistida (com ID) para um método REQUIRES_NEW
 * se a transação externa ainda segura um lock nessa linha.
 * Prefira passar objetos transientes ou apenas IDs primitivos.
 */
@Service
public class TransferAuditService {

    private static final Logger log = LoggerFactory.getLogger(TransferAuditService.class);

    private final TransferRepository transferRepository;

    public TransferAuditService(TransferRepository transferRepository) {
        this.transferRepository = transferRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Transfer auditSuccess(Transfer transfer) {
        log.info("Auditing transfer success for key={}", transfer.getIdempotencyKey());
        transfer.complete();
        return transferRepository.save(transfer); // INSERT — objeto transiente
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Transfer auditFailure(Transfer transfer, String reason) {
        log.warn("Auditing transfer failure for key={}, reason={}", transfer.getIdempotencyKey(), reason);
        transfer.fail(reason);
        return transferRepository.save(transfer); // INSERT — objeto transiente
    }
}
