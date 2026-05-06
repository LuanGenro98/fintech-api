package com.fintech.api.shared.aop;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Aspect de logging: registra automaticamente tempo de execução
 * de todos os métodos da camada de serviço.
 *
 * EXAME — Esse é o caso de uso mais clássico de AOP:
 * Cross-cutting concerns (preocupações transversais) — comportamentos
 * que se repetem em vários lugares e não pertencem à lógica de negócio:
 *   - Logging / Auditoria
 *   - Segurança
 *   - Transações (@Transactional é um Aspect do próprio Spring!)
 *   - Cache
 *   - Idempotência (nosso caso anterior)
 *
 * Sem AOP, você colocaria System.currentTimeMillis() em CADA método
 * de CADA serviço. Com AOP, declara uma vez e aplica em todos.
 *
 * EXAME — Ordem de execução quando há múltiplos Aspects:
 *   Por padrão, a ordem não é garantida entre Aspects diferentes.
 *   Use @Order(n) para controlar: menor número = maior prioridade (executa primeiro).
 *   Ex: @Order(1) no LoggingAspect, @Order(2) no IdempotencyAspect
 *   → Log envolve tudo por fora, Idempotency verifica por dentro.
 */
@Aspect
@Component
public class LoggingAspect {

    private static final Logger log = LoggerFactory.getLogger(LoggingAspect.class);

    /**
     * Pointcut: qualquer método em qualquer classe anotada com @Service.
     *
     * EXAME — @within vs @annotation:
     *   @within(Service) → seleciona todos os métodos de classes com @Service
     *   @annotation(Service) → selecionaria métodos com @Service diretamente
     *
     * Aqui usamos @within para capturar todos os métodos dos serviços
     * sem precisar anotar cada método individualmente.
     */
    @Pointcut("@within(org.springframework.stereotype.Service)")
    public void serviceMethods() {}

    /**
     * Loga o método chamado, seus argumentos e o tempo de execução.
     *
     * EXAME — Tratamento de exceção em @Around:
     *   Se proceed() lançar uma exceção, ela DEVE ser propagada (re-lançada)
     *   para que o Spring possa fazer rollback da transação e o
     *   GlobalExceptionHandler possa tratá-la corretamente.
     *   Engolir a exceção aqui quebraria o mecanismo de transações.
     */
    @Around("serviceMethods()")
    public Object logExecutionTime(ProceedingJoinPoint joinPoint) throws Throwable {
        String className  = joinPoint.getTarget().getClass().getSimpleName();
        String methodName = joinPoint.getSignature().getName();
        String fullName   = className + "." + methodName + "()";

        log.debug("→ Entering {}", fullName);

        long start = System.currentTimeMillis();
        try {
            Object result = joinPoint.proceed(); // executa o método original
            long elapsed = System.currentTimeMillis() - start;

            log.debug("← {} completed in {}ms", fullName, elapsed);
            return result;

        } catch (Throwable ex) {
            long elapsed = System.currentTimeMillis() - start;

            // Loga o erro mas RE-LANÇA — nunca engula exceções em Aspects!
            log.warn("← {} failed in {}ms — {}: {}",
                    fullName, elapsed, ex.getClass().getSimpleName(), ex.getMessage());
            throw ex;
        }
    }
}