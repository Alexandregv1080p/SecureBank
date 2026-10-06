package com.securebank.pix.infrastructure;

import com.securebank.pix.application.PixScheduleApplicationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/** Agendador do Pix agendado: a cada minuto executa o que venceu (várias instâncias são seguras: trava com SKIP LOCKED). */
@Configuration
@EnableScheduling
class PixScheduleConfig {

    private static final Logger log = LoggerFactory.getLogger(PixScheduleConfig.class);

    @Bean
    @ConditionalOnProperty(name = "securebank.pix.scheduler.enabled", havingValue = "true", matchIfMissing = true)
    PixScheduleRunner pixScheduleRunner(PixScheduleApplicationService schedules) {
        return new PixScheduleRunner(schedules);
    }

    static class PixScheduleRunner {

        private final PixScheduleApplicationService schedules;

        PixScheduleRunner(PixScheduleApplicationService schedules) {
            this.schedules = schedules;
        }

        @Scheduled(fixedDelayString = "${securebank.pix.scheduler.poll-interval-ms:60000}")
        void run() {
            try {
                int handled = schedules.executeDue();
                if (handled > 0) {
                    log.info("Scheduled Pix handled: {}", handled);
                }
            } catch (RuntimeException e) {
                log.error("Scheduled Pix run failed; will retry on the next tick", e);
            }
        }
    }
}
