package com.fintech.api.account;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Teste de Specifications com @DataJpaTest.
 *
 * EXAME — @DataJpaTest:
 *  - Sobe apenas a fatia JPA: repositórios, EntityManager, DataSource (H2)
 *  - NÃO sobe: @Service, @Controller, @Component
 *  - Cada @Test roda em uma transação que é revertida ao final (isolamento automático)
 *  - TestEntityManager: wrapper do EntityManager para testes — persist + flush em uma linha
 *
 * EXAME — @Nested:
 *  Organiza testes relacionados em classes internas.
 *  Cada classe @Nested pode ter seu próprio @BeforeEach.
 *  Facilita leitura e agrupa cenários por comportamento.
 *
 * EXAME — @ParameterizedTest:
 *  Executa o mesmo teste com múltiplas entradas.
 *  Elimina duplicação — um método, N cenários.
 *  Providers mais cobrados no exame:
 *   @ValueSource        → valores literais (strings, ints, etc.)
 *   @NullAndEmptySource → null e "" (ótimo para testar opcionais)
 *   @MethodSource       → método estático que retorna Stream<Arguments>
 *   @CsvSource          → valores em formato CSV inline
 *   @EnumSource         → todos ou alguns valores de um enum
 */
@DataJpaTest
@ActiveProfiles("test")
@DisplayName("AccountSpecifications — filtros dinâmicos")
class AccountSpecificationsTest {

    @Autowired
    private TestEntityManager em;

    @Autowired
    private AccountRepository accountRepository;

    /**
     * TestEntityManager.persistAndFlush():
     *  - persist() → adiciona à sessão JPA
     *  - flush()   → executa o INSERT imediatamente (não espera o commit)
     *  Isso garante que o dado está visível para queries na mesma transação.
     */
    @BeforeEach
    void setUp() {
        em.persistAndFlush(new Account("Alice Silva",   "S-001", new BigDecimal("5000.00")));
        em.persistAndFlush(new Account("Bob Santos",    "S-002", new BigDecimal("1500.00")));
        em.persistAndFlush(new Account("Carol Alves",   "S-003", new BigDecimal("8000.00")));
        em.persistAndFlush(new Account("David Lima",    "S-004", new BigDecimal("300.00")));
        em.persistAndFlush(new Account("Alice Mendes",  "S-005", new BigDecimal("2200.00")));
    }

    // ── Filtro: ownerName ─────────────────────────────────────────────────────

    @Nested
    @DisplayName("hasOwnerName()")
    class HasOwnerNameSpec {

        @ParameterizedTest(name = "ownerName={0} → deve retornar todas as contas")
        @NullAndEmptySource
        @ValueSource(strings = {"   "})
        @DisplayName("Filtro ausente/vazio/branco → retorna todas as contas")
        void shouldReturnAllWhenFilterIsBlank(String ownerName) {
            // EXAME — @NullAndEmptySource: testa null e "" automaticamente
            // @ValueSource adiciona " " (branco) — cobrindo os 3 casos de "ausência"
            Specification<Account> spec = Specification
                    .where(AccountSpecifications.hasOwnerName(ownerName));

            Page<Account> result = accountRepository.findAll(spec, PageRequest.of(0, 10));

            assertThat(result.getTotalElements()).isEqualTo(5);
        }

        @ParameterizedTest(name = "ownerName=\"{0}\" → deve retornar {1} conta(s)")
        @MethodSource("ownerNameCases")
        @DisplayName("Filtro por nome (parcial, case-insensitive)")
        void shouldFilterByOwnerName(String ownerName, int expectedCount) {
            // EXAME — @MethodSource: aponta para método estático que retorna Stream<Arguments>
            // Permite cenários complexos com múltiplos parâmetros por execução
            Specification<Account> spec = Specification
                    .where(AccountSpecifications.hasOwnerName(ownerName));

            Page<Account> result = accountRepository.findAll(spec, PageRequest.of(0, 10));

            assertThat(result.getTotalElements()).isEqualTo(expectedCount);
        }

        static Stream<Arguments> ownerNameCases() {
            return Stream.of(
                    Arguments.of("Alice",  2),  // Alice Silva + Alice Mendes
                    Arguments.of("alice",  2),  // case-insensitive
                    Arguments.of("ALICE",  2),  // case-insensitive maiúsculo
                    Arguments.of("Santos", 1),  // só Bob Santos
                    Arguments.of("xyz",    0)   // nenhum resultado
            );
        }
    }

    // ── Filtro: minBalance / maxBalance ───────────────────────────────────────

    @Nested
    @DisplayName("hasMinBalance() + hasMaxBalance()")
    class BalanceRangeSpec {

        @ParameterizedTest(name = "min={0}, max={1} → esperado={2} conta(s)")
        @MethodSource("balanceRangeCases")
        @DisplayName("Filtro por faixa de saldo")
        void shouldFilterByBalanceRange(BigDecimal min, BigDecimal max, int expectedCount) {
            Specification<Account> spec = Specification
                    .where(AccountSpecifications.hasMinBalance(min))
                    .and(AccountSpecifications.hasMaxBalance(max));

            Page<Account> result = accountRepository.findAll(spec, PageRequest.of(0, 10));

            assertThat(result.getTotalElements()).isEqualTo(expectedCount);
        }

        static Stream<Arguments> balanceRangeCases() {
            return Stream.of(
                    // só min
                    Arguments.of(new BigDecimal("5000"), null, 2),   // Alice(5000) + Carol(8000)
                    // só max
                    Arguments.of(null, new BigDecimal("1500"), 2),   // Bob(1500) + David(300)
                    // faixa completa
                    Arguments.of(new BigDecimal("1500"), new BigDecimal("5000"), 3), // Bob+Alice+Alice
                    // nenhum filtro → tudo
                    Arguments.of(null, null, 5),
                    // faixa sem resultado
                    Arguments.of(new BigDecimal("9000"), new BigDecimal("10000"), 0)
            );
        }
    }

    // ── Combinação de filtros ─────────────────────────────────────────────────

    @Nested
    @DisplayName("Combinação de filtros (explosion test)")
    class CombinedFiltersSpec {

        @ParameterizedTest(name = "ownerName={0}, min={1}, max={2} → esperado={3}")
        @MethodSource("combinedFilterCases")
        @DisplayName("Todas as combinações de filtros opcionais")
        void shouldHandleAllFilterCombinations(String ownerName, BigDecimal min,
                                               BigDecimal max, int expectedCount) {
            // Este teste prova o valor das Specifications:
            // 8 combinações cobertas com UM método e UM repositório.findAll()
            Specification<Account> spec = Specification
                    .where(AccountSpecifications.hasOwnerName(ownerName))
                    .and(AccountSpecifications.hasMinBalance(min))
                    .and(AccountSpecifications.hasMaxBalance(max));

            Page<Account> result = accountRepository.findAll(spec, PageRequest.of(0, 10));

            assertThat(result.getTotalElements()).isEqualTo(expectedCount);
        }

        static Stream<Arguments> combinedFilterCases() {
            return Stream.of(
                    // nenhum filtro
                    Arguments.of(null, null, null, 5),
                    // só nome
                    Arguments.of("Alice", null, null, 2),
                    // só min
                    Arguments.of(null, new BigDecimal("2000"), null, 3),
                    // só max
                    Arguments.of(null, null, new BigDecimal("2000"), 2),
                    // nome + min
                    Arguments.of("Alice", new BigDecimal("3000"), null, 1),  // só Alice Silva(5000)
                    // nome + max
                    Arguments.of("Alice", null, new BigDecimal("3000"), 1),  // só Alice Mendes(2200)
                    // min + max
                    Arguments.of(null, new BigDecimal("1000"), new BigDecimal("6000"), 3),
                    // todos os filtros
                    Arguments.of("Alice", new BigDecimal("1000"), new BigDecimal("6000"), 2)
            );
        }
    }

    // ── Paginação ─────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Paginação e ordenação")
    class PaginationSpec {

        @ParameterizedTest(name = "page={0}, size={1} → conteúdo={2}, totalPages={3}")
        @MethodSource("paginationCases")
        @DisplayName("Paginação correta sobre resultados filtrados")
        void shouldPaginateCorrectly(int page, int size, int expectedContent, int expectedTotalPages) {
            // Sem filtro — 5 contas no total
            Specification<Account> spec = Specification.where(null);

            Page<Account> result = accountRepository.findAll(
                    spec, PageRequest.of(page, size, Sort.by("ownerName")));

            assertThat(result.getContent()).hasSize(expectedContent);
            assertThat(result.getTotalPages()).isEqualTo(expectedTotalPages);
            assertThat(result.getTotalElements()).isEqualTo(5);
        }

        static Stream<Arguments> paginationCases() {
            return Stream.of(
                    Arguments.of(0, 2, 2, 3),  // página 0, 2 por página → 3 páginas
                    Arguments.of(1, 2, 2, 3),  // página 1
                    Arguments.of(2, 2, 1, 3),  // página 2 (última, só 1 elemento)
                    Arguments.of(0, 5, 5, 1),  // tudo em 1 página
                    Arguments.of(0, 10, 5, 1)  // tamanho maior que total
            );
        }
    }
}
