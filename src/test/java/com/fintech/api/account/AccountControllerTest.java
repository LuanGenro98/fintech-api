package com.fintech.api.account;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Slice test para a camada web (AccountController).
 *
 * EXAME — @WebMvcTest:
 *  - Sobe apenas a camada web: controllers, filters, @ControllerAdvice, WebMvcConfigurer
 *  - NÃO instancia @Service, @Repository — eles precisam ser mockados com @MockBean
 *  - @MockBean registra um mock Mockito E o substitui no ApplicationContext
 *    (diferente de @Mock do Mockito puro, que não interage com o contexto Spring)
 *
 * EXAME — MockMvc:
 *  - Simula requisições HTTP sem subir um servidor real (sem porta, sem TCP)
 *  - perform() → executa a requisição
 *  - andExpect() → verifica o resultado (status, headers, body JSON)
 *  - jsonPath() → navega no JSON usando JSONPath expressions
 */
@WebMvcTest(AccountController.class)
@DisplayName("AccountController Slice Tests")
class AccountControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    // @MockBean registra o mock NO contexto Spring (necessário pois @WebMvcTest
    // não instancia @Service — o controller precisa do bean para ser criado)
    @MockBean
    private AccountService accountService;

    @Test
    @DisplayName("POST /api/v1/accounts → 201 Created")
    void shouldCreateAccountAndReturn201() throws Exception {
        // given
        var request = new AccountController.CreateAccountRequest(
                "Alice Silva", "ACC-001", new BigDecimal("5000.00"));

        Account fakeAccount = buildFakeAccount(1L, "Alice Silva", "ACC-001", new BigDecimal("5000.00"));

        when(accountService.create(any(), any(), any())).thenReturn(fakeAccount);

        // when / then
        mockMvc.perform(post("/api/v1/accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.accountNumber").value("ACC-001"))
                .andExpect(jsonPath("$.balance").value(5000.00));
    }

    @Test
    @DisplayName("POST /api/v1/accounts → 400 when body is invalid")
    void shouldReturn400WhenBodyIsInvalid() throws Exception {
        // Body inválido: ownerName vazio, balance negativo
        String invalidBody = """
                {
                  "ownerName": "",
                  "accountNumber": "ACC-001",
                  "initialBalance": -100
                }
                """;

        mockMvc.perform(post("/api/v1/accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Validation Failed"));
    }

    @Test
    @DisplayName("GET /api/v1/accounts/{id} → 404 when not found")
    void shouldReturn404WhenAccountNotFound() throws Exception {
        when(accountService.findById(99L)).thenThrow(new AccountNotFoundException(99L));

        mockMvc.perform(get("/api/v1/accounts/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Resource Not Found"));
    }

    // ── Helper ───────────────────────────────────────────────────────────────

    private Account buildFakeAccount(Long id, String owner, String number, BigDecimal balance) {
        // Reflection-free: usamos o construtor público e simulamos o estado via reflexão
        // Em projetos reais, use um builder ou test factory
        try {
            Account account = new Account(owner, number, balance);
            var idField = Account.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(account, id);
            var dateField = Account.class.getDeclaredField("createdAt");
            dateField.setAccessible(true);
            dateField.set(account, LocalDateTime.now());
            return account;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
