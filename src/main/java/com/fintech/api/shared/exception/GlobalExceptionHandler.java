package com.fintech.api.shared.exception;

import com.fintech.api.account.AccountNotFoundException;
import com.fintech.api.account.DuplicateAccountException;
import com.fintech.api.account.InsufficientFundsException;
import com.fintech.api.transfer.TransferNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Tratamento centralizado de exceções.
 *
 * EXAME — @ControllerAdvice / @RestControllerAdvice:
 *  - @ControllerAdvice aplica a todos os controllers (pode filtrar por pacote/tipo).
 *  - @RestControllerAdvice = @ControllerAdvice + @ResponseBody
 *  - @ExceptionHandler mapeia um tipo de exceção para um método tratador.
 *  - O Spring MVC procura o handler mais específico para a exceção lançada.
 *
 * EXAME — ProblemDetail (RFC 7807):
 *  Spring 6+ tem suporte nativo a Problem Details para APIs REST.
 *  Padroniza o formato de erros HTTP (type, title, status, detail, instance).
 *
 * EXAME — MethodArgumentNotValidException:
 *  Lançada quando @Valid falha. Contém todos os erros de validação.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    // ── 404 Not Found ─────────────────────────────────────────────────────────

    @ExceptionHandler({AccountNotFoundException.class, TransferNotFoundException.class})
    public ResponseEntity<ProblemDetail> handleNotFound(RuntimeException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        problem.setTitle("Resource Not Found");
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(problem);
    }

    // ── 409 Conflict ──────────────────────────────────────────────────────────

    @ExceptionHandler(DuplicateAccountException.class)
    public ResponseEntity<ProblemDetail> handleDuplicate(DuplicateAccountException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        problem.setTitle("Duplicate Resource");
        return ResponseEntity.status(HttpStatus.CONFLICT).body(problem);
    }

    // ── 422 Unprocessable Entity ──────────────────────────────────────────────

    @ExceptionHandler(InsufficientFundsException.class)
    public ResponseEntity<ProblemDetail> handleInsufficientFunds(InsufficientFundsException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage());
        problem.setTitle("Insufficient Funds");
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(problem);
    }

    // ── 400 Bad Request — Falha de validação (@Valid) ─────────────────────────

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ProblemDetail> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> errors = ex.getBindingResult().getFieldErrors()
                .stream()
                .collect(Collectors.toMap(
                        FieldError::getField,
                        fe -> fe.getDefaultMessage() != null ? fe.getDefaultMessage() : "Invalid value"
                ));

        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        problem.setTitle("Validation Failed");
        problem.setDetail("One or more fields have invalid values");
        problem.setProperty("errors", errors);
        problem.setProperty("timestamp", LocalDateTime.now());
        return ResponseEntity.badRequest().body(problem);
    }

    // ── 500 Internal Server Error — fallback ──────────────────────────────────

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleGeneral(Exception ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred");
        problem.setTitle("Internal Server Error");
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(problem);
    }
}
