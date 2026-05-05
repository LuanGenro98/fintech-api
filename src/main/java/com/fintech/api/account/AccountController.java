package com.fintech.api.account;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
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
 */
@RestController
@RequestMapping("/api/v1/accounts")
public class AccountController {

    private final AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    @GetMapping
    public List<AccountResponse> findAll() {
        return accountService.findAll()
                .stream()
                .map(AccountResponse::from)
                .toList();
    }

    @GetMapping("/{id}")
    public AccountResponse findById(@PathVariable Long id) {
        return AccountResponse.from(accountService.findById(id));
    }

    @PostMapping
    public ResponseEntity<AccountResponse> create(@Valid @RequestBody CreateAccountRequest request) {
        Account account = accountService.create(
                request.ownerName(),
                request.accountNumber(),
                request.initialBalance()
        );

        URI location = ServletUriComponentsBuilder
                .fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(account.getId())
                .toUri();

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
            @NotBlank(message = "Owner name is required")
            String ownerName,

            @NotBlank(message = "Account number is required")
            String accountNumber,

            @NotNull(message = "Initial balance is required")
            @PositiveOrZero(message = "Initial balance cannot be negative")
            BigDecimal initialBalance
    ) {}

    public record AccountResponse(
            Long id,
            String ownerName,
            String accountNumber,
            BigDecimal balance,
            String createdAt
    ) {
        static AccountResponse from(Account account) {
            return new AccountResponse(
                    account.getId(),
                    account.getOwnerName(),
                    account.getAccountNumber(),
                    account.getBalance(),
                    account.getCreatedAt().toString()
            );
        }
    }
}
