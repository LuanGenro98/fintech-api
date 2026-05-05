package com.fintech.api.shared.config;

import com.fintech.api.account.Account;
import com.fintech.api.account.AccountRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import java.math.BigDecimal;

/**
 * Configuração de dados iniciais para desenvolvimento.
 *
 * EXAME — @Configuration:
 *  Marca a classe como fonte de definições de beans (@Bean).
 *  É uma especialização de @Component — também é detectada pelo @ComponentScan.
 *
 * EXAME — @Profile:
 *  Este bean só é criado quando o profile "dev" está ativo.
 *  Em produção (profile "prod"), este bean NÃO existe no ApplicationContext.
 *  Pode ser combinado: @Profile({"dev", "test"})
 *  Pode ser negado:   @Profile("!prod")
 *
 * EXAME — CommandLineRunner:
 *  Functional interface executada após o ApplicationContext estar pronto.
 *  Útil para inicialização de dados, migrations, etc.
 *  Alternativa: ApplicationRunner (recebe ApplicationArguments ao invés de String[])
 *
 * EXAME — @Bean:
 *  Registra o retorno do método como um bean no ApplicationContext.
 *  O nome do bean padrão é o nome do método.
 */
@Configuration
@Profile("dev")
public class DataInitializerConfig {

    private static final Logger log = LoggerFactory.getLogger(DataInitializerConfig.class);

    @Bean
    public CommandLineRunner initData(AccountRepository accountRepository) {
        return args -> {
            log.info("=== [DEV] Initializing sample data ===");

            Account alice = accountRepository.save(
                    new Account("Alice Silva", "ACC-001", new BigDecimal("5000.00")));

            Account bob = accountRepository.save(
                    new Account("Bob Santos", "ACC-002", new BigDecimal("3000.00")));

            Account carol = accountRepository.save(
                    new Account("Carol Oliveira", "ACC-003", new BigDecimal("10000.00")));

            log.info("Created accounts: {}, {}, {}", alice.getAccountNumber(),
                    bob.getAccountNumber(), carol.getAccountNumber());
        };
    }
}
