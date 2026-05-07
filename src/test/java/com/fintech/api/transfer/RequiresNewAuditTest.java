package com.fintech.api.transfer;

import com.fintech.api.account.Account;
import com.fintech.api.account.AccountRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Testa o comportamento do REQUIRES_NEW na auditoria de transferências.
 *
 * EXAME — O que este teste prova:
 *   Quando execute() lança exceção, a transação principal faz rollback.
 *   MAS o registro de auditoria (status=FAILED) foi commitado em uma
 *   transação independente (REQUIRES_NEW) e permanece no banco.
 *
 *   Sem REQUIRES_NEW: rollback apagaria o registro — auditoria perdida.
 *   Com REQUIRES_NEW: auditoria sobrevive ao rollback — comportamento correto.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
class RequiresNewAuditTest {

    @Autowired
    private TransferService transferService;

    @Autowired
    private TransferRepository transferRepository;

    @Autowired
    private AccountRepository accountRepository;

    private static final String SOURCE      = "AUDIT-001";
    private static final String DESTINATION = "AUDIT-002";

    @BeforeEach
    void setUp() {
        transferRepository.deleteAll();
        accountRepository.findAll().stream()
                .filter(a -> a.getAccountNumber().startsWith("AUDIT-"))
                .forEach(accountRepository::delete);

        // Conta com saldo insuficiente para provocar falha
        accountRepository.save(new Account("Fonte Audit",   SOURCE,      new BigDecimal("100.00")));
        accountRepository.save(new Account("Destino Audit", DESTINATION, new BigDecimal("0.00")));
    }

    @Test
    @DisplayName("Registro FAILED deve persistir mesmo após rollback da transação principal")
    void shouldPersistFailedAuditEvenAfterRollback() {
        String idempotencyKey = "audit-requires-new-test";

        // Tenta transferir mais do que o saldo disponível
        assertThatThrownBy(() ->
                transferService.execute(idempotencyKey, SOURCE, DESTINATION, new BigDecimal("500.00")))
                .hasMessageContaining("insufficient funds");

        // A transação principal fez rollback — mas o registro de auditoria
        // foi salvo em REQUIRES_NEW e deve ter sobrevivido
        Transfer audit = transferRepository.findByIdempotencyKey(idempotencyKey)
                .orElseThrow(() -> new AssertionError(
                        "Registro de auditoria não encontrado! " +
                        "Sem REQUIRES_NEW, o rollback teria apagado este registro."));

        assertThat(audit.getStatus())
                .as("Status deve ser FAILED")
                .isEqualTo(Transfer.TransferStatus.FAILED);

        assertThat(audit.getFailureReason())
                .as("Motivo da falha deve estar registrado")
                .isNotBlank();

        // Saldo não deve ter sido alterado (rollback funcionou)
        BigDecimal saldoFinal = accountRepository
                .findByAccountNumber(SOURCE).orElseThrow().getBalance();

        assertThat(saldoFinal)
                .as("Saldo deve permanecer intacto após rollback")
                .isEqualByComparingTo("100.00");

        System.out.println("""

                ══════════════════════════════════════════════════════
                REQUIRES_NEW FUNCIONANDO:
                - Transação principal: ROLLBACK (saldo intacto)
                - Transação de auditoria: COMMIT (registro salvo)
                - Status: %s
                - Motivo: %s
                ══════════════════════════════════════════════════════
                """.formatted(audit.getStatus(), audit.getFailureReason()));
    }

    @Test
    @DisplayName("Transferência bem-sucedida deve ter registro COMPLETED persistido")
    void shouldPersistCompletedAudit() {
        String idempotencyKey = "audit-success-test";

        Transfer result = transferService.execute(
                idempotencyKey, SOURCE, DESTINATION, new BigDecimal("50.00"));

        assertThat(result.getStatus())
                .isEqualTo(Transfer.TransferStatus.COMPLETED);

        // Saldo debitado corretamente
        BigDecimal saldoFinal = accountRepository
                .findByAccountNumber(SOURCE).orElseThrow().getBalance();

        assertThat(saldoFinal)
                .isEqualByComparingTo("50.00");
    }
}
