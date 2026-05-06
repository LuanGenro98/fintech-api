package com.fintech.api.shared.aop;

import com.fintech.api.transfer.Transfer;
import com.fintech.api.transfer.TransferRepository;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Aspect de idempotência para transferências.
 *
 * EXAME — Anatomia de um Aspect:
 *
 * @Aspect  → marca a classe como um Aspect (não registra como bean sozinho!)
 * @Component → registra no ApplicationContext (ambos são necessários)
 *
 * EXAME — PEGADINHA:
 *   @Aspect sozinho NÃO registra o bean. Sem @Component (ou @Bean em uma
 *   @Configuration), o Aspect existe como classe mas NUNCA é aplicado.
 *   A aplicação sobe sem erro. Silenciosamente não funciona.
 *
 * EXAME — Como o Spring AOP funciona (Proxy Pattern):
 *   O Spring cria um PROXY em volta do bean alvo (TransferService).
 *   Quando alguém chama transferService.execute(), na verdade está
 *   chamando o proxy, que intercepta e executa os Advices.
 *   Por isso AOP NÃO funciona em self-invocation (chamar o próprio método
 *   internamente pula o proxy).
 *
 * EXAME — Dois tipos de proxy:
 *   JDK Dynamic Proxy  → usado quando o bean implementa uma interface
 *   CGLIB Proxy        → usado quando o bean NÃO implementa interface (subclasse)
 *   Spring Boot usa CGLIB por padrão desde o Spring 5.
 */
@Aspect
@Component
public class IdempotencyAspect {

    private static final Logger log = LoggerFactory.getLogger(IdempotencyAspect.class);

    private final TransferRepository transferRepository;

    public IdempotencyAspect(TransferRepository transferRepository) {
        this.transferRepository = transferRepository;
    }

    /**
     * Pointcut: seleciona todos os métodos anotados com @Idempotent.
     *
     * EXAME — Sintaxe de Pointcut expressions (as mais cobradas):
     *
     * execution(* com.fintech..*.*(..))
     *   → qualquer método em qualquer classe dentro de com.fintech
     *
     * @annotation(com.fintech.api.shared.aop.Idempotent)
     *   → qualquer método anotado com @Idempotent  ← o que usamos aqui
     *
     * within(com.fintech.api.transfer.*)
     *   → qualquer método em qualquer classe do pacote transfer
     *
     * @within(org.springframework.stereotype.Service)
     *   → qualquer método em classes anotadas com @Service
     *
     * bean(transferService)
     *   → específico do Spring AOP: métodos do bean chamado "transferService"
     *
     * Pointcuts podem ser combinados com &&, ||, !
     * Ex: execution(* *.*(..)) && @annotation(Idempotent)
     */
    @Pointcut("@annotation(com.fintech.api.shared.aop.Idempotent)")
    public void idempotentMethods() {}

    /**
     * Advice @Around: intercepta o método anotado com @Idempotent.
     *
     * Fluxo:
     * 1. Extrai a idempotency key dos argumentos do método
     * 2. Busca no banco se já existe uma transferência com essa chave
     * 3. Se existe → retorna a transferência existente (sem chamar o método)
     * 4. Se não existe → chama o método original (proceed()) e retorna o resultado
     *
     * EXAME — ProceedingJoinPoint:
     *   - proceed()         → executa o método original com os args originais
     *   - proceed(args)     → executa com argumentos modificados (poderoso!)
     *   - getArgs()         → retorna os argumentos da chamada
     *   - getSignature()    → retorna informações do método interceptado
     *   - getTarget()       → retorna o objeto alvo (o bean real, não o proxy)
     *
     * EXAME — @Around DEVE retornar Object (ou o tipo de retorno do método).
     *   Se você esquecer o return proceed(), o método original é engolido
     *   e retorna null — outro bug silencioso clássico.
     */
    @Around("idempotentMethods()")
    public Object checkIdempotency(ProceedingJoinPoint joinPoint) throws Throwable {

        // Extrai a annotation para ler o keyArgumentIndex configurado
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Idempotent annotation = signature.getMethod().getAnnotation(Idempotent.class);

        // Extrai a idempotency key do argumento correto
        Object[] args = joinPoint.getArgs();
        String idempotencyKey = (String) args[annotation.keyArgumentIndex()];

        log.debug("Checking idempotency for key: {}", idempotencyKey);

        // Busca no banco se já existe transferência com essa chave
        Optional<Transfer> existing = transferRepository.findByIdempotencyKey(idempotencyKey);

        if (existing.isPresent()) {
            log.info("Idempotency hit: returning existing transfer for key={}, id={}",
                    idempotencyKey, existing.get().getId());

            // Retorna o resultado existente SEM chamar o método original
            // O cliente recebe exatamente o que receberia na 1ª chamada
            return existing.get();
        }

        // Chave nova — deixa o método original executar normalmente
        log.debug("Idempotency miss: proceeding with execution for key={}", idempotencyKey);
        return joinPoint.proceed();
    }
}