package com.fintech.api.shared.aop;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation customizada que marca métodos que devem ser protegidos
 * contra execução duplicada (idempotência).
 *
 * EXAME — Meta-anotações importantes:
 *
 * @Target(ElementType.METHOD)
 *   → Define ONDE esta annotation pode ser usada.
 *   Valores comuns: METHOD, TYPE, FIELD, PARAMETER, CONSTRUCTOR.
 *   Se omitido, pode ser usada em qualquer lugar.
 *
 * @Retention(RetentionPolicy.RUNTIME)
 *   → Define até quando a annotation é mantida:
 *   - SOURCE  → só no código fonte, descartada pelo compilador
 *   - CLASS   → mantida no .class, mas não disponível em runtime (padrão)
 *   - RUNTIME → disponível via Reflection em runtime ← obrigatório para AOP
 *
 * EXAME — PEGADINHA:
 *   Se você esquecer @Retention(RUNTIME), o Spring AOP não consegue
 *   detectar a annotation em runtime e o Aspect NUNCA é acionado.
 *   A aplicação sobe sem erro — o Aspect simplesmente não funciona.
 *   É um dos bugs mais difíceis de debugar.
 *
 * O parâmetro `keyArgumentIndex` indica qual argumento do método
 * anotado contém a chave de idempotência.
 * Ex: execute(String idempotencyKey, ...) → index 0
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Idempotent {

    /**
     * Índice do argumento que contém a chave de idempotência.
     * Padrão: 0 (primeiro argumento).
     */
    int keyArgumentIndex() default 0;
}