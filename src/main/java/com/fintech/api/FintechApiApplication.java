package com.fintech.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Ponto de entrada da aplicação.
 *
 * @SpringBootApplication é uma meta-anotação que combina:
 *   - @Configuration        → marca esta classe como fonte de beans Spring
 *   - @EnableAutoConfiguration → ativa a mágica do Spring Boot (detecta dependências no classpath
 *                               e configura beans automaticamente, ex: DataSource, JPA, MVC)
 *   - @ComponentScan        → varre o pacote atual e sub-pacotes em busca de @Component,
 *                             @Service, @Repository, @Controller, etc.
 *
 * EXAME: Saiba o que cada anotação composta faz individualmente.
 */
@SpringBootApplication
public class FintechApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(FintechApiApplication.class, args);
    }
}
