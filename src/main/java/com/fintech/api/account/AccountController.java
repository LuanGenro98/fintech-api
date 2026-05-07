package com.fintech.api.account;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.math.BigDecimal;
import java.net.URI;
import java.util.List;

/**
 * Controller REST para operações de conta.
 *
 * EXAME — Anotações MVC:
 *  - @RestController = @Controller + @ResponseBody
 *    (cada método retorna o objeto serializado como JSON, não uma view)
 *  - @RequestMapping → URL base do controller
 *  - @GetMapping, @PostMapping... → atalhos para @RequestMapping(method=...)
 *  - @PathVariable  → extrai variável da URL  (/accounts/{id})
 *  - @RequestBody   → desserializa o corpo JSON para um objeto Java
 *  - @Valid         → dispara Bean Validation no objeto recebido
 *
 * EXAME — ResponseEntity:
 *  Permite controle total sobre status HTTP, headers e body da resposta.
 *
 * EXAME — Paginação no Controller:
 *
 * Pageable é resolvido automaticamente pelo Spring MVC a partir dos query params:
 *   ?page=0&size=10&sort=ownerName,asc&sort=balance,desc
 *
 * @PageableDefault define os valores padrão quando o cliente não envia os params.
 *
 * EXAME — Por que retornar Page<T> ao invés de List<T>?
 *  Page carrega metadados essenciais para o cliente:
 *  totalElements, totalPages, number (página atual), size, first, last.
 *  Sem isso, o cliente não sabe quantas páginas existem para navegar.
 *
 * EXAME — @RequestParam(required = false):
 *  Torna o query param opcional. Se não enviado, o valor é null.
 *  Funciona perfeitamente com as Specifications que tratam null como "ignorar".
 */
@RestController
@RequestMapping("/api/v1/accounts")
public class AccountController {

    private final AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    /**
     * Busca paginada com filtros opcionais.
     * Exemplos:
     *   GET /api/v1/accounts/search                              → tudo
     *   GET /api/v1/accounts/search?ownerName=alice             → por nome
     *   GET /api/v1/accounts/search?minBalance=100&maxBalance=500 → por faixa de saldo
     *   GET /api/v1/accounts/search?ownerName=bob&minBalance=50&page=0&size=5
     */
    @GetMapping("/search")
    public Page<AccountResponse> search(
            @RequestParam(required = false) String ownerName,
            @RequestParam(required = false) BigDecimal minBalance,
            @RequestParam(required = false) BigDecimal maxBalance,
            @PageableDefault(size = 10, sort = "ownerName") Pageable pageable) {

        return accountService.search(ownerName, minBalance, maxBalance, pageable)
                .map(AccountResponse::from);
    }

    @GetMapping
    public List<AccountResponse> findAll() {
        return accountService.findAll().stream().map(AccountResponse::from).toList();
    }

    @GetMapping("/{id}")
    public AccountResponse findById(@PathVariable Long id) {
        return AccountResponse.from(accountService.findById(id));
    }

    @PostMapping
    public ResponseEntity<AccountResponse> create(@Valid @RequestBody CreateAccountRequest request) {
        Account account = accountService.create(
                request.ownerName(), request.accountNumber(), request.initialBalance());

        URI location = ServletUriComponentsBuilder
                .fromCurrentRequest().path("/{id}")
                .buildAndExpand(account.getId()).toUri();

        return ResponseEntity.created(location).body(AccountResponse.from(account));
    }

    // ── DTOs internos (Records — Java 16+) ────────────────────────────────────

    /**
     * EXAME — Boas práticas:
     * Usar DTOs (Data Transfer Objects) ao invés de expor a entidade diretamente.
     * Records são ideais para DTOs imutáveis.
     *
     * @Valid no @RequestBody + anotações de Bean Validation nos campos do record
     * disparam a validação automaticamente.
     */
    public record CreateAccountRequest(
            @NotBlank(message = "Owner name is required") String ownerName,
            @NotBlank(message = "Account number is required") String accountNumber,
            @NotNull @PositiveOrZero BigDecimal initialBalance
    ) {}

    public record AccountResponse(
            Long id, String ownerName, String accountNumber,
            BigDecimal balance, String createdAt
    ) {
        static AccountResponse from(Account account) {
            return new AccountResponse(account.getId(), account.getOwnerName(),
                    account.getAccountNumber(), account.getBalance(),
                    account.getCreatedAt().toString());
        }
    }
}
