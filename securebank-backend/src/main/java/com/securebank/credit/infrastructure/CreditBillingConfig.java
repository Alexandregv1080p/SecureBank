package com.securebank.credit.infrastructure;

import com.securebank.credit.application.CreditBillingService;
import com.securebank.shared.application.BankTime;
import com.securebank.shared.domain.CardId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * Todo dia o crédito põe a casa em ordem: multa e juros de faturas vencidas e os avisos de fatura. Roda de hora em hora e
 * cada cartão só é processado uma vez por dia (e tudo é idempotente), então um reinício ou duas instâncias não duplicam nada.
 */
@Configuration
@EnableScheduling
class CreditBillingConfig {

    private static final Logger log = LoggerFactory.getLogger(CreditBillingConfig.class);

    @Bean
    @ConditionalOnProperty(name = "securebank.credit.scheduler.enabled", havingValue = "true", matchIfMissing = true)
    BillingRunner creditBillingRunner(CreditBillingService billing, BankTime time) {
        return new BillingRunner(billing, time);
    }

    static class BillingRunner {

        private final CreditBillingService billing;
        private final BankTime time;

        BillingRunner(CreditBillingService billing, BankTime time) {
            this.billing = billing;
            this.time = time;
        }

        @Scheduled(fixedDelayString = "${securebank.credit.scheduler.poll-interval-ms:3600000}", initialDelayString = "${securebank.credit.scheduler.initial-delay-ms:30000}")
        void run() {
            var today = time.today();
            int done = 0;
            try {
                for (CardId id : billing.cardsDue(today)) {
                    try {
                        billing.bill(id, today);
                        done++;
                    } catch (RuntimeException e) {
                        log.error("Credit billing failed for card {}; will retry on the next tick", id, e);
                    }
                }
                if (done > 0) {
                    log.info("Credit billing processed {} cards", done);
                }
            } catch (RuntimeException e) {
                log.error("Credit billing run failed; will retry on the next tick", e);
            }
        }
    }
}
