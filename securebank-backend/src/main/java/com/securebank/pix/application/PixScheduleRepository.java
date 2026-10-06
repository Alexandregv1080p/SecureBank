package com.securebank.pix.application;

import com.securebank.pix.domain.PixSchedule;
import com.securebank.shared.application.PageResult;
import com.securebank.shared.domain.CustomerId;
import com.securebank.shared.domain.PixScheduleId;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface PixScheduleRepository {

    Optional<PixSchedule> findById(PixScheduleId id);

    /**
     * O agendamento, travado para escrita, SE ainda está pendente e vencido. {@code SKIP LOCKED}: com várias instâncias da
     * API rodando o agendador, quem chegar depois simplesmente pula (nunca duas execuções do mesmo Pix agendado).
     */
    Optional<PixSchedule> lockDue(PixScheduleId id, LocalDate today);

    List<PixScheduleId> findDueIds(LocalDate today, int limit);

    PageResult<PixSchedule> findByCustomer(CustomerId customerId, int page, int size);

    long countPendingByCustomer(CustomerId customerId);

    void save(PixSchedule schedule);
}
