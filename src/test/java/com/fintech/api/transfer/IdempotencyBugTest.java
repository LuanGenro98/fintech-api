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

import static org.assertj.core.api.Assertions.*;

/**
 * Teste de integração que PROVA o bug de idempotência.
 *
 * EXAME — @SpringBootTest:
 *  - Sobe o ApplicationContext COMPLETO (web server, banco, todos os beans)
 *  - webEnvironment = NONE → não sobe servidor HTTP (mais rápido, suficiente aqui)
 *  - Diferente de @WebMvcTest (só web) e @DataJpaTest (só JPA)
 *
 * EXAME — @ActiveProfiles:
 *  - Ativa o profile "test", que pode ter configurações específicas
 *  - Aqui reusamos o H2 em memória do profile padrão
 *  - O DataInitializerConfig tem @Profile("dev"), então NÃO roda nos testes
 *
 * EXAME — @Transactional em testes:
 *  - Quando usada no teste, cada @Test roda em uma transação que é revertida ao final
 *  - Garante isolamento entre testes sem precisar limpar o banco manualmente
 *  - ATENÇÃO: não usamos @Transactional aqui propositalmente — precisamos que os
 *    dados persistam entre chamadas para simular requisições HTTP independentes
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
class IdempotencyBugTest {

    @Autowired
    private TransferService transferService;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private TransferRepository transferRepository;

    private static final String IDEMPOTENCY_KEY = "abc-123-bug-test";
    private static final String SOURCE      = "BUG-001";
    private static final String DESTINATION = "BUG-002";
    private static final BigDecimal INITIAL_BALANCE  = new BigDecimal("1000.00");
    private static final BigDecimal TRANSFER_AMOUNT  = new BigDecimal("200.00");

    /**
     * Cria contas frescas antes de cada teste.
     * Como não usamos @Transactional na classe, limpamos manualmente.
     */
    @BeforeEach
    void setUp() {
        // Limpa dados de execuções anteriores (H2 em memória persiste entre testes)
        transferRepository.deleteAll();
        accountRepository.findAll().stream()
                .filter(a -> a.getAccountNumber().startsWith("BUG-"))
                .forEach(accountRepository::delete);

        accountRepository.save(new Account("Fonte Bug",   SOURCE,      INITIAL_BALANCE));
        accountRepository.save(new Account("Destino Bug", DESTINATION, INITIAL_BALANCE));
    }

    @Test
    @DisplayName("🐛 BUG: mesma Idempotency-Key processa a transferência DUAS vezes")
    void shouldDebitTwiceWhenSameKeyIsSentAgain() {
        // ── 1ª requisição: transferência legítima ──────────────────────────────
        transferService.execute(IDEMPOTENCY_KEY, SOURCE, DESTINATION, TRANSFER_AMOUNT);

        BigDecimal balanceAfterFirst = accountRepository
                .findByAccountNumber(SOURCE).orElseThrow().getBalance();

        // Saldo esperado após 1ª transferência: 1000 - 200 = 800
        assertThat(balanceAfterFirst)
                .as("Saldo após 1ª transferência deve ser 800")
                .isEqualByComparingTo("800.00");

        // ── Simula o cenário de timeout: cliente reenvia a mesma requisição ────
        //
        // Na vida real, o cliente não sabe que o servidor já processou.
        // Ele só sabe que não recebeu resposta, então reenvia.
        //
        // Aqui simulamos isso chamando execute() novamente com a mesma chave.
        // O que DEVERIA acontecer: retornar o resultado já existente.
        // O que REALMENTE acontece agora: lança exceção de constraint única
        // (o banco rejeita a segunda inserção do idempotencyKey duplicado),
        // mas ANTES disso, o débito/crédito JÁ TERIAM sido executados se
        // não houvesse a constraint. Em um banco sem constraint, o dinheiro
        // sairia duas vezes.
        //
        // Este teste documenta o comportamento atual (bugado) e vai FALHAR
        // depois que implementarmos o AOP de idempotência — o que é exatamente
        // o que queremos: o teste se torna a prova de que o fix funcionou.

        // ── 2ª requisição: mesma chave (retry simulado) ────────────────────────
        assertThatThrownBy(() ->
                transferService.execute(IDEMPOTENCY_KEY, SOURCE, DESTINATION, TRANSFER_AMOUNT))
                .as("Sem idempotência: o banco lança erro de constraint duplicada")
                .isInstanceOf(Exception.class); // DataIntegrityViolationException ou similar

        // Verifica o estado final do saldo
        BigDecimal balanceAfterRetry = accountRepository
                .findByAccountNumber(SOURCE).orElseThrow().getBalance();

        // ── Aqui está o bug documentado ───────────────────────────────────────
        //
        // CENÁRIO ATUAL (com constraint de BD):
        // A exceção foi lançada, então o rollback reverteu o débito da 2ª tentativa.
        // O saldo ainda é 800 — parece OK, mas só porque o BD nos salvou acidentalmente.
        //
        // CENÁRIO SEM CONSTRAINT (o perigo real):
        // Em um banco sem unique constraint no idempotencyKey, o saldo seria 600.
        // A constraint é uma rede de segurança, não a solução correta.
        // A solução correta é a aplicação verificar ANTES de processar.
        //
        // Este comentário é o coração do teste: documentar por que precisamos do AOP.
        System.out.println("""
                
                ══════════════════════════════════════════════════════
                BUG DOCUMENTADO:
                - Saldo inicial:           R$ 1000.00
                - Após 1ª transferência:   R$ %s
                - Após retry (sem AOP):    R$ %s
                
                Sem a constraint do BD, o saldo seria R$ 600.00.
                A constraint é acidental — não é a solução.
                A solução é o AOP interceptar ANTES de processar.
                ══════════════════════════════════════════════════════
                """.formatted(balanceAfterFirst, balanceAfterRetry));

        // O teste passa porque o BD salvou com a constraint.
        // Depois do fix com AOP, este comportamento muda:
        // a 2ª chamada NÃO lançará exceção — retornará a transferência original.
        assertThat(balanceAfterRetry)
                .as("Saldo não deve ter sido debitado duas vezes")
                .isEqualByComparingTo("800.00");
    }

    @Test
    @DisplayName("✅ COMPORTAMENTO ESPERADO após fix: retry deve retornar transferência original")
    void shouldReturnExistingTransferOnRetry() {
        // ── 1ª requisição ─────────────────────────────────────────────────────
        Transfer firstResult = transferService.execute(
                IDEMPOTENCY_KEY, SOURCE, DESTINATION, TRANSFER_AMOUNT);

        // ── 2ª requisição (retry) ─────────────────────────────────────────────
        // ESTE TESTE VAI FALHAR AGORA.
        // Depois do AOP, ele deve passar: a 2ª chamada retorna a 1ª transferência.
        //
        // É o "teste vermelho" do TDD — escrevemos o comportamento desejado
        // ANTES de implementá-lo. Quando ficar verde, o fix está completo.
        Transfer retryResult = transferService.execute(
                IDEMPOTENCY_KEY, SOURCE, DESTINATION, TRANSFER_AMOUNT);

        // Deve retornar EXATAMENTE a mesma transferência
        assertThat(retryResult.getId())
                .as("Retry deve retornar o ID da transferência original")
                .isEqualTo(firstResult.getId());

        // Saldo deve ter sido debitado apenas UMA vez
        BigDecimal finalBalance = accountRepository
                .findByAccountNumber(SOURCE).orElseThrow().getBalance();

        assertThat(finalBalance)
                .as("Saldo deve ser debitado apenas uma vez")
                .isEqualByComparingTo("800.00");
    }
}
