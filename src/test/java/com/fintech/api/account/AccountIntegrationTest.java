package com.fintech.api.account;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Teste de integração — cenário MOCK (WebEnvironment padrão).
 *
 * EXAME — Diferença CRÍTICA entre @WebMvcTest e @SpringBootTest + MockMvc:
 *
 *  @WebMvcTest:
 *   - Sobe APENAS a camada web (controllers, filters, ControllerAdvice)
 *   - @Service e @Repository NÃO existem → precisam ser @MockBean
 *   - Mais rápido, mas não testa integração real entre camadas
 *   - Ideal para testar lógica do controller isoladamente
 *
 *  @SpringBootTest + @AutoConfigureMockMvc:
 *   - Sobe o ApplicationContext COMPLETO (todas as camadas)
 *   - @Service e @Repository são REAIS — sem mocks
 *   - MockMvc faz requisições ao DispatcherServlet simulado
 *   - Testa o fluxo completo: Controller → Service → Repository → H2
 *   - Mais lento, mas garante que as camadas funcionam juntas
 *
 * EXAME — @AutoConfigureMockMvc:
 *   Configura e injeta o MockMvc automaticamente no contexto do @SpringBootTest.
 *   Sem ela, MockMvc não é criado — você precisaria instanciá-lo manualmente.
 *
 * EXAME — @Transactional em testes de integração:
 *   Cada @Test roda em uma transação revertida ao final.
 *   Garante isolamento sem precisar limpar o banco manualmente.
 *   ATENÇÃO: não use @Transactional quando testar comportamento
 *   transacional do próprio código (ex: REQUIRES_NEW) — o rollback
 *   automático pode esconder bugs reais.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@DisplayName("Account — Teste de Integração (WebEnvironment.MOCK)")
class AccountIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AccountRepository accountRepository;

    @BeforeEach
    void setUp() {
        accountRepository.save(new Account("Alice Silva",  "IT-001", new BigDecimal("5000.00")));
        accountRepository.save(new Account("Bob Santos",   "IT-002", new BigDecimal("1500.00")));
        accountRepository.save(new Account("Carol Alves",  "IT-003", new BigDecimal("8000.00")));
    }

    // ── GET /api/v1/accounts ──────────────────────────────────────────────────

    @Nested
    @DisplayName("GET /api/v1/accounts")
    class FindAll {

        @Test
        @DisplayName("Deve retornar todas as contas com status 200")
        void shouldReturnAllAccounts() throws Exception {
            mockMvc.perform(get("/api/v1/accounts"))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$", hasSize(3)))
                    .andExpect(jsonPath("$[*].ownerName",
                            containsInAnyOrder("Alice Silva", "Bob Santos", "Carol Alves")));
        }
    }

    // ── GET /api/v1/accounts/search ───────────────────────────────────────────

    @Nested
    @DisplayName("GET /api/v1/accounts/search — Specifications + Paginação")
    class Search {

        @Test
        @DisplayName("Sem filtros → retorna todas as contas paginadas")
        void shouldReturnAllWhenNoFilters() throws Exception {
            mockMvc.perform(get("/api/v1/accounts/search"))
                    .andExpect(status().isOk())
                    // EXAME — Page<T> serializa metadados de paginação no JSON:
                    .andExpect(jsonPath("$.totalElements").value(3))
                    .andExpect(jsonPath("$.totalPages").value(1))
                    .andExpect(jsonPath("$.content", hasSize(3)));
        }

        @Test
        @DisplayName("Filtro por ownerName → retorna apenas contas que batem")
        void shouldFilterByOwnerName() throws Exception {
            mockMvc.perform(get("/api/v1/accounts/search")
                            .param("ownerName", "alice"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalElements").value(1))
                    .andExpect(jsonPath("$.content[0].ownerName").value("Alice Silva"));
        }

        @Test
        @DisplayName("Filtro por faixa de saldo → retorna contas no intervalo")
        void shouldFilterByBalanceRange() throws Exception {
            mockMvc.perform(get("/api/v1/accounts/search")
                            .param("minBalance", "1000")
                            .param("maxBalance", "6000"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalElements").value(2));
        }

        @Test
        @DisplayName("Paginação → retorna página e tamanho corretos")
        void shouldPaginateResults() throws Exception {
            mockMvc.perform(get("/api/v1/accounts/search")
                            .param("page", "0")
                            .param("size", "2")
                            .param("sort", "ownerName,asc"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content", hasSize(2)))
                    .andExpect(jsonPath("$.totalPages").value(2))
                    .andExpect(jsonPath("$.first").value(true))
                    .andExpect(jsonPath("$.last").value(false));
        }
    }

    // ── POST /api/v1/accounts ─────────────────────────────────────────────────

    @Nested
    @DisplayName("POST /api/v1/accounts")
    class Create {

        @Test
        @DisplayName("Conta válida → 201 Created com Location header")
        void shouldCreateAccountAndReturn201() throws Exception {
            var request = new AccountController.CreateAccountRequest(
                    "Daniel Costa", "IT-099", new BigDecimal("2000.00"));

            mockMvc.perform(post("/api/v1/accounts")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andExpect(header().string("Location", containsString("/api/v1/accounts/")))
                    .andExpect(jsonPath("$.ownerName").value("Daniel Costa"))
                    .andExpect(jsonPath("$.balance").value(2000.00));
        }

        @Test
        @DisplayName("Número de conta duplicado → 409 Conflict")
        void shouldReturn409WhenAccountNumberAlreadyExists() throws Exception {
            // IT-001 já existe no setUp()
            var request = new AccountController.CreateAccountRequest(
                    "Outro Dono", "IT-001", new BigDecimal("100.00"));

            mockMvc.perform(post("/api/v1/accounts")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.title").value("Duplicate Resource"));
        }

        @Test
        @DisplayName("Body inválido → 400 com detalhes de validação")
        void shouldReturn400WhenBodyIsInvalid() throws Exception {
            String invalidBody = """
                    {
                      "ownerName": "",
                      "accountNumber": "IT-100",
                      "initialBalance": -50
                    }
                    """;

            mockMvc.perform(post("/api/v1/accounts")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(invalidBody))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.title").value("Validation Failed"))
                    .andExpect(jsonPath("$.errors.ownerName").exists())
                    .andExpect(jsonPath("$.errors.initialBalance").exists());
        }

        @Test
        @DisplayName("Body ausente → 400")
        void shouldReturn400WhenBodyIsMissing() throws Exception {
            mockMvc.perform(post("/api/v1/accounts")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isBadRequest());
        }
    }

    // ── GET /api/v1/accounts/{id} ─────────────────────────────────────────────

    @Nested
    @DisplayName("GET /api/v1/accounts/{id}")
    class FindById {

        @Test
        @DisplayName("ID existente → 200 com dados corretos")
        void shouldReturnAccountWhenFound() throws Exception {
            Long id = accountRepository.findByAccountNumber("IT-001")
                    .orElseThrow().getId();

            mockMvc.perform(get("/api/v1/accounts/{id}", id))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.accountNumber").value("IT-001"))
                    .andExpect(jsonPath("$.ownerName").value("Alice Silva"));
        }

        @Test
        @DisplayName("ID inexistente → 404 com ProblemDetail")
        void shouldReturn404WhenNotFound() throws Exception {
            mockMvc.perform(get("/api/v1/accounts/999999"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.title").value("Resource Not Found"));
        }
    }
}
