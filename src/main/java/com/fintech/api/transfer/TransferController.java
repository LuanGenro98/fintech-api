package com.fintech.api.transfer;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.math.BigDecimal;
import java.net.URI;
import java.util.List;

/**
 * Controller de transferências.
 *
 * EXAME — Cabeçalhos HTTP:
 * O cliente envia o header "Idempotency-Key" com um UUID único.
 * Na próxima sessão (AOP), criaremos um Aspect que intercepta este
 * controller e garante idempotência ANTES de chamar o serviço.
 *
 * @RequestHeader("Idempotency-Key") → extrai o valor do cabeçalho HTTP
 */
@RestController
@RequestMapping("/api/v1/transfers")
public class TransferController {

    private final TransferService transferService;

    public TransferController(TransferService transferService) {
        this.transferService = transferService;
    }

    @GetMapping
    public List<TransferResponse> findAll() {
        return transferService.findAll()
                .stream()
                .map(TransferResponse::from)
                .toList();
    }

    @GetMapping("/{id}")
    public TransferResponse findById(@PathVariable Long id) {
        return TransferResponse.from(transferService.findById(id));
    }

    /**
     * Executa uma transferência.
     *
     * O header "Idempotency-Key" é OBRIGATÓRIO.
     * Futuro: um AOP Aspect vai interceptar esta chamada e verificar
     * se já existe uma transferência com essa chave antes de processar.
     */
    @PostMapping
    public ResponseEntity<TransferResponse> execute(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody TransferRequest request) {

        Transfer transfer = transferService.execute(
                idempotencyKey,
                request.sourceAccountNumber(),
                request.destinationAccountNumber(),
                request.amount()
        );

        URI location = ServletUriComponentsBuilder
                .fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(transfer.getId())
                .toUri();

        return ResponseEntity.created(location).body(TransferResponse.from(transfer));
    }

    // ── DTOs ─────────────────────────────────────────────────────────────────

    public record TransferRequest(
            @NotBlank String sourceAccountNumber,
            @NotBlank String destinationAccountNumber,
            @NotNull @Positive BigDecimal amount
    ) {}

    public record TransferResponse(
            Long id,
            String idempotencyKey,
            String sourceAccountNumber,
            String destinationAccountNumber,
            BigDecimal amount,
            String status,
            String failureReason,
            String createdAt
    ) {
        static TransferResponse from(Transfer t) {
            return new TransferResponse(
                    t.getId(),
                    t.getIdempotencyKey(),
                    t.getSourceAccountNumber(),
                    t.getDestinationAccountNumber(),
                    t.getAmount(),
                    t.getStatus().name(),
                    t.getFailureReason(),
                    t.getCreatedAt().toString()
            );
        }
    }
}
