package com.fintech.api.account;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Teste unitário do AccountService.
 *
 * EXAME — Tipos de teste no Spring:
 *
 * 1. UNIT TEST (este arquivo):
 *    - Não sobe o ApplicationContext
 *    - @ExtendWith(MockitoExtension.class) → integra JUnit 5 + Mockito
 *    - @Mock → cria mock do colaborador
 *    - @InjectMocks → injeta os mocks no objeto testado
 *    - Mais rápido, isolado, testa a lógica pura
 *
 * 2. SLICE TEST (@WebMvcTest, @DataJpaTest):
 *    - Sobe apenas uma fatia do contexto
 *    - @WebMvcTest → só a camada web (controllers, filters, MVC config)
 *    - @DataJpaTest → só a camada de dados (repositórios, JPA)
 *
 * 3. INTEGRATION TEST (@SpringBootTest):
 *    - Sobe o ApplicationContext completo
 *    - Mais lento, mas testa a integração real entre camadas
 *
 * EXAME — AssertJ (assertThat) vs JUnit assertions:
 *  AssertJ é fluente e mais expressivo — preferido nos projetos Spring modernos.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AccountService Unit Tests")
class AccountServiceTest {

    @Mock
    private AccountRepository accountRepository;

    @InjectMocks
    private AccountService accountService;

    @Test
    @DisplayName("Should create account successfully")
    void shouldCreateAccountSuccessfully() {
        // given
        String ownerName = "Alice";
        String accountNumber = "ACC-999";
        BigDecimal balance = new BigDecimal("1000.00");
        Account savedAccount = new Account(ownerName, accountNumber, balance);

        when(accountRepository.existsByAccountNumber(accountNumber)).thenReturn(false);
        when(accountRepository.save(any(Account.class))).thenReturn(savedAccount);

        // when
        Account result = accountService.create(ownerName, accountNumber, balance);

        // then
        assertThat(result.getOwnerName()).isEqualTo(ownerName);
        assertThat(result.getAccountNumber()).isEqualTo(accountNumber);
        assertThat(result.getBalance()).isEqualByComparingTo(balance);

        verify(accountRepository).existsByAccountNumber(accountNumber);
        verify(accountRepository).save(any(Account.class));
    }

    @Test
    @DisplayName("Should throw DuplicateAccountException when account number already exists")
    void shouldThrowWhenAccountNumberAlreadyExists() {
        // given
        when(accountRepository.existsByAccountNumber("ACC-001")).thenReturn(true);

        // when / then
        assertThatThrownBy(() ->
                accountService.create("Bob", "ACC-001", BigDecimal.TEN))
                .isInstanceOf(DuplicateAccountException.class)
                .hasMessageContaining("ACC-001");

        // Garante que save() NUNCA foi chamado
        verify(accountRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should throw AccountNotFoundException when account not found by id")
    void shouldThrowWhenAccountNotFoundById() {
        // given
        when(accountRepository.findById(99L)).thenReturn(Optional.empty());

        // when / then
        assertThatThrownBy(() -> accountService.findById(99L))
                .isInstanceOf(AccountNotFoundException.class)
                .hasMessageContaining("99");
    }
}
