package com.securebank.investment.infrastructure;

import com.securebank.investment.application.InvestmentApplicationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/** Avisa o cliente quando uma aplicação com prazo vence (a cada minuto; várias instâncias são seguras: SKIP LOCKED). */
@Configuration
@EnableScheduling
class InvestmentMaturityConfig {

    private static final Logger log = LoggerFactory.getLogger(InvestmentMaturityConfig.class);

    @Bean
    @ConditionalOnProperty(name = "securebank.investment.scheduler.enabled", havingValue = "true", matchIfMissing = true)
    MaturityRunner maturityRunner(InvestmentApplicationService investments) {
        return new MaturityRunner(investments);
    }

    static class MaturityRunner {

        private final InvestmentApplicationService investments;

        MaturityRunner(InvestmentApplicationService investments) {
            this.investments = investments;
        }

        @Scheduled(fixedDelayString = "${securebank.investment.scheduler.poll-interval-ms:60000}")
        void run() {
            try {
                int notified = investments.notifyMatured();
                if (notified > 0) {
                    log.info("Investment maturities notified: {}", notified);
                }
            } catch (RuntimeException e) {
                log.error("Investment maturity run failed; will retry on the next tick", e);
            }
        }
    }
}
