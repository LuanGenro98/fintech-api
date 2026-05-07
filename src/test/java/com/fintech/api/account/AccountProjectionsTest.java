package com.fintech.api.account;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * EXAME — O que este teste valida:
 *
 * 1. Interface-based Projection → campos corretos, @Value SpEL calculado
 * 2. Class-based Projection     → constructor expression, campo calculado
 * 3. Dynamic Projection         → mesmo método, tipos diferentes de retorno
 *
 * Padrão: @DataJpaTest + TestEntityManager para setup
 * Cada @Test tem rollback automático → isolamento garantido
 */
@DataJpaTest
@ActiveProfiles("test")
@DisplayName("AccountProjections — tipos e comportamentos")
class AccountProjectionsTest {

    @Autowired
    private TestEntityManager em;

    @Autowired
    private AccountRepository accountRepository;

    @BeforeEach
    void setUp() {
        em.persistAndFlush(new Account("Alice Silva",  "P-001", new BigDecimal("6000.00")));
        em.persistAndFlush(new Account("Bob Santos",   "P-002", new BigDecimal("1500.00")));
        em.persistAndFlush(new Account("Carol Alves",  "P-003", new BigDecimal("400.00")));
    }

    // ── Interface-based Projection ────────────────────────────────────────────

    @Nested
    @DisplayName("Interface-based Projection (AccountSummary)")
    class InterfaceBasedProjection {

        @Test
        @DisplayName("Deve retornar apenas id, ownerName e accountNumber")
        void shouldReturnOnlySummaryFields() {
            List<AccountSummary> result = accountRepository.findAllProjectedBy();

            assertThat(result).hasSize(3);
            assertThat(result).allSatisfy(summary -> {
                assertThat(summary.getId()).isNotNull();
                assertThat(summary.getOwnerName()).isNotBlank();
                assertThat(summary.getAccountNumber()).isNotBlank();
            });
        }

        @Test
        @DisplayName("@Value SpEL deve calcular displayName corretamente")
        void shouldCalculateDisplayNameViaSpEL() {
            List<AccountSummary> result = accountRepository.findAllProjectedBy();

            // @Value("#{target.ownerName + ' (' + target.accountNumber + ')'}") 
            // → "Alice Silva (P-001)"
            assertThat(result)
                    .extracting(AccountSummary::getDisplayName)
                    .containsExactlyInAnyOrder(
                            "Alice Silva (P-001)",
                            "Bob Santos (P-002)",
                            "Carol Alves (P-003)"
                    );
        }

        @Test
        @DisplayName("Dynamic Projection com interface deve retornar AccountSummary")
        void shouldUseDynamicProjectionWithInterface() {
            Optional<AccountSummary> result = accountRepository
                    .findByAccountNumber("P-001", AccountSummary.class);

            assertThat(result).isPresent();
            assertThat(result.get().getOwnerName()).isEqualTo("Alice Silva");
            assertThat(result.get().getDisplayName()).isEqualTo("Alice Silva (P-001)");
        }
    }

    // ── Class-based Projection ────────────────────────────────────────────────

    @Nested
    @DisplayName("Class-based Projection (AccountBalanceView)")
    class ClassBasedProjection {

        @Test
        @DisplayName("Deve retornar ownerName e balance corretamente")
        void shouldReturnBalanceView() {
            List<AccountBalanceView> result =
                    accountRepository.findAllBalanceViews();

            assertThat(result).hasSize(3);
            assertThat(result)
                    .extracting(AccountBalanceView::ownerName)
                    .containsExactlyInAnyOrder("Alice Silva", "Bob Santos", "Carol Alves");
        }

        @ParameterizedTest(name = "balance={0} → categoria={1}")
        @MethodSource("balanceCategoryArgs")
        @DisplayName("Campo calculado balanceCategory deve categorizar corretamente")
        void shouldCategorizeBalance(BigDecimal balance, String expectedCategory) {
            // Testa a lógica do construtor do record diretamente
            // Sem precisar ir ao banco — teste unitário puro dentro do @DataJpaTest
            AccountBalanceView view =
                    new AccountBalanceView("Test", balance);

            assertThat(view.balanceCategory()).isEqualTo(expectedCategory);
        }

        static Stream<Arguments> balanceCategoryArgs() {
            return Stream.of(
                    Arguments.of(new BigDecimal("5000.00"), "HIGH"),    // >= 5000
                    Arguments.of(new BigDecimal("8000.00"), "HIGH"),
                    Arguments.of(new BigDecimal("1000.00"), "MEDIUM"),  // >= 1000
                    Arguments.of(new BigDecimal("4999.99"), "MEDIUM"),
                    Arguments.of(new BigDecimal("999.99"),  "LOW"),     // < 1000
                    Arguments.of(new BigDecimal("0.00"),    "LOW")
            );
        }

        @Test
        @DisplayName("Deve aplicar categoria correta às contas persistidas")
        void shouldApplyCategoryToPersistedAccounts() {
            List<AccountBalanceView> result =
                    accountRepository.findAllBalanceViews();

            assertThat(result)
                    .extracting(AccountBalanceView::balanceCategory)
                    .containsExactlyInAnyOrder("HIGH", "MEDIUM", "LOW");
        }
    }

    // ── Dynamic Projection ────────────────────────────────────────────────────

    @Nested
    @DisplayName("Dynamic Projection — mesmo método, tipos diferentes")
    class DynamicProjection {

        @Test
        @DisplayName("Class<Account> → retorna entidade completa")
        void shouldReturnFullEntityWhenAccountClassPassed() {
            Optional<Account> result = accountRepository
                    .findByAccountNumber("P-001", Account.class);

            assertThat(result).isPresent();
            // Entidade completa — todos os campos disponíveis
            assertThat(result.get().getBalance()).isEqualByComparingTo("6000.00");
            assertThat(result.get().getVersion()).isNotNull();
        }

        @Test
        @DisplayName("Class<AccountSummary> → retorna apenas campos da interface")
        void shouldReturnSummaryWhenInterfacePassed() {
            Optional<AccountSummary> result = accountRepository
                    .findByAccountNumber("P-001", AccountSummary.class);

            assertThat(result).isPresent();
            assertThat(result.get().getOwnerName()).isEqualTo("Alice Silva");
            // balance não existe em AccountSummary → o campo não foi buscado no banco
        }

        @Test
        @DisplayName("Dynamic Projection retorna empty quando não encontrado")
        void shouldReturnEmptyWhenNotFound() {
            Optional<AccountSummary> result = accountRepository
                    .findByAccountNumber("NAO-EXISTE", AccountSummary.class);

            assertThat(result).isEmpty();
        }
    }
}
