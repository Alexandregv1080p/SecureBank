package com.securebank.shared.application;

import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Executa o trabalho numa transação NOVA a cada tentativa e repete quando outra requisição alterou o mesmo agregado
 * (conflito de versão ou deadlock/falha de lock, todos ConcurrencyFailureException). Cada tentativa relê o estado atual, então regras como saldo e limite diário são reavaliadas:
 * é isso que impede o duplo gasto sem travar linhas. Sem sucesso após {@value #MAX_ATTEMPTS} tentativas, o conflito
 * sobe (409) e o cliente pode repetir com a mesma Idempotency-Key.
 * Quem chama NÃO pode estar dentro de uma transação (senão a repetição releria dados do mesmo snapshot).
 */
@Component
public class TransactionalRetry {

    static final int MAX_ATTEMPTS = 6;

    private final TransactionTemplate tx;

    public TransactionalRetry(PlatformTransactionManager transactionManager) {
        this.tx = new TransactionTemplate(transactionManager);
    }

    public <T> T execute(Supplier<T> work) {
        for (int attempt = 1; ; attempt++) {
            try {
                return tx.execute(status -> work.get());
            } catch (ConcurrencyFailureException e) {
                if (attempt == MAX_ATTEMPTS) {
                    throw e;
                }
                backoff(attempt);
            }
        }
    }

    /** Espera crescente com jitter: quem perdeu a corrida não volta no mesmo instante que os outros. */
    private static void backoff(int attempt) {
        try {
            Thread.sleep(5L * attempt + ThreadLocalRandom.current().nextLong(0, 20));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
