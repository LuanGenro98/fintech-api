package com.fintech.api.transfer;

import com.fintech.api.account.Account;
import com.fintech.api.account.AccountRepository;
import com.fintech.api.transfer.TransferController.TransferRequest;
import com.fintech.api.transfer.TransferController.TransferResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Teste de integração — cenário RANDOM_PORT.
 *
 * EXAME — Por que RANDOM_PORT aqui?
 *  Transferências envolvem headers HTTP customizados (Idempotency-Key),
 *  comportamento transacional real e status codes específicos.
 *  RANDOM_PORT sobe um servidor HTTP real — testamos o stack completo
 *  incluindo serialização HTTP, resolução de headers, filtros de servlet.
 *
 * EXAME — TestRestTemplate vs MockMvc:
 *
 *  MockMvc (MOCK):
 *   - Requisições simuladas — não passam por TCP
 *   - Mais rápido, DSL fluente com andExpect()
 *   - Ideal para maioria dos testes de integração
 *
 *  TestRestTemplate (RANDOM_PORT):
 *   - Requisições HTTP reais via TCP
 *   - Retorna ResponseEntity<T> — trabalha com objetos Java
 *   - Ideal para testar headers, cookies, redirects, comportamento HTTP real
 *   - @LocalServerPort injeta a porta aleatória escolhida pelo SO
 *
 * EXAME — TestRestTemplate é thread-safe e não lança exceção em
 *  respostas de erro (4xx, 5xx) — diferente do RestTemplate padrão.
 *  Você verifica o status via response.getStatusCode().
 *
 * EXAME — @Transactional NÃO funciona aqui:
 *  O teste roda em um processo/thread diferente do servidor.
 *  Rollback automático não é possível — limpamos o banco no @BeforeEach.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@DisplayName("Transfer — Teste de Integração (WebEnvironment.RANDOM_PORT)")
class TransferIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private TransferRepository transferRepository;

    private static final String SOURCE      = "TR-001";
    private static final String DESTINATION = "TR-002";

    @BeforeEach
    void setUp() {
        // Sem @Transactional — limpeza manual obrigatória com RANDOM_PORT
        transferRepository.deleteAll();
        accountRepository.findAll().stream()
                .filter(a -> a.getAccountNumber().startsWith("TR-"))
                .forEach(accountRepository::delete);

        accountRepository.save(new Account("Fonte",   SOURCE,      new BigDecimal("1000.00")));
        accountRepository.save(new Account("Destino", DESTINATION, new BigDecimal("500.00")));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private HttpHeaders headersWithKey(String idempotencyKey) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Idempotency-Key", idempotencyKey);
        return headers;
    }

    private ResponseEntity<TransferResponse> postTransfer(
            String idempotencyKey, String source, String destination, BigDecimal amount) {

        var request = new TransferRequest(source, destination, amount);
        var entity  = new HttpEntity<>(request, headersWithKey(idempotencyKey));
        return restTemplate.postForEntity("/api/v1/transfers", entity, TransferResponse.class);
    }

    // ── Transferência bem-sucedida ────────────────────────────────────────────

    @Nested
    @DisplayName("POST /api/v1/transfers — transferência válida")
    class SuccessfulTransfer {

        @Test
        @DisplayName("Deve retornar 201 e status COMPLETED")
        void shouldReturn201AndCompleted() {
            ResponseEntity<TransferResponse> response =
                    postTransfer("key-success-1", SOURCE, DESTINATION, new BigDecimal("200.00"));

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().status()).isEqualTo("COMPLETED");
            assertThat(response.getHeaders().getLocation()).isNotNull();
        }

        @Test
        @DisplayName("Saldo deve ser debitado após transferência")
        void shouldDebitSourceBalance() {
            postTransfer("key-debit-1", SOURCE, DESTINATION, new BigDecimal("300.00"));

            BigDecimal balance = accountRepository
                    .findByAccountNumber(SOURCE).orElseThrow().getBalance();

            assertThat(balance).isEqualByComparingTo("700.00");
        }
    }

    // ── Idempotência via HTTP real ─────────────────────────────────────────────

    @Nested
    @DisplayName("Idempotência — mesmo header, mesmo resultado")
    class IdempotencyViaHttp {

        @Test
        @DisplayName("Retry com mesma Idempotency-Key → retorna transferência original")
        void shouldReturnSameTransferOnRetry() {
            String key = "key-idempotency-http";

            // 1ª requisição
            ResponseEntity<TransferResponse> first =
                    postTransfer(key, SOURCE, DESTINATION, new BigDecimal("100.00"));

            // 2ª requisição — retry simulado
            ResponseEntity<TransferResponse> second =
                    postTransfer(key, SOURCE, DESTINATION, new BigDecimal("100.00"));

            assertThat(first.getBody()).isNotNull();
            assertThat(second.getBody()).isNotNull();

            // Mesmo ID — mesma transferência retornada
            assertThat(second.getBody().id()).isEqualTo(first.getBody().id());

            // Saldo debitado apenas uma vez
            BigDecimal balance = accountRepository
                    .findByAccountNumber(SOURCE).orElseThrow().getBalance();
            assertThat(balance).isEqualByComparingTo("900.00");
        }
    }

    // ── Transferência com falha ────────────────────────────────────────────────

    @Nested
    @DisplayName("POST /api/v1/transfers — cenários de falha")
    class FailedTransfer {

        @Test
        @DisplayName("Saldo insuficiente → 422 Unprocessable Entity")
        void shouldReturn422WhenInsufficientFunds() {
            ResponseEntity<String> response = restTemplate.postForEntity(
                    "/api/v1/transfers",
                    new HttpEntity<>(
                            new TransferRequest(SOURCE, DESTINATION, new BigDecimal("9999.00")),
                            headersWithKey("key-insufficient")),
                    String.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        }

        @Test
        @DisplayName("Conta origem inexistente → 404 Not Found")
        void shouldReturn404WhenSourceAccountNotFound() {
            ResponseEntity<String> response = restTemplate.postForEntity(
                    "/api/v1/transfers",
                    new HttpEntity<>(
                            new TransferRequest("NAO-EXISTE", DESTINATION, new BigDecimal("100.00")),
                            headersWithKey("key-not-found")),
                    String.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }

        @Test
        @DisplayName("Idempotency-Key ausente → 400 Bad Request")
        void shouldReturn400WhenIdempotencyKeyMissing() {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            // sem Idempotency-Key

            ResponseEntity<String> response = restTemplate.postForEntity(
                    "/api/v1/transfers",
                    new HttpEntity<>(
                            new TransferRequest(SOURCE, DESTINATION, new BigDecimal("100.00")),
                            headers),
                    String.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @Test
        @DisplayName("Registro FAILED deve persistir após saldo insuficiente")
        void shouldPersistFailedAuditAfterInsufficientFunds() {
            String key = "key-audit-http";

            restTemplate.postForEntity(
                    "/api/v1/transfers",
                    new HttpEntity<>(
                            new TransferRequest(SOURCE, DESTINATION, new BigDecimal("9999.00")),
                            headersWithKey(key)),
                    String.class);

            // Registro de auditoria deve existir mesmo após rollback da Tx principal
            Transfer audit = transferRepository.findByIdempotencyKey(key).orElseThrow();
            assertThat(audit.getStatus()).isEqualTo(Transfer.TransferStatus.FAILED);
            assertThat(audit.getFailureReason()).isNotBlank();
        }
    }
}
