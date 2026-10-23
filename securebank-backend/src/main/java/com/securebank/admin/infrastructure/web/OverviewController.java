package com.securebank.admin.infrastructure.web;

import com.securebank.shared.domain.InvalidValueException;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Visão gerencial do banco (só ADMIN, permissão VIEW_REPORTS): clientes, contas, movimento, crédito e sinais de segurança.
 * Só leitura e só agregados: nenhum dado pessoal sai daqui. Valores em reais; o câmbio e as carteiras em outras moedas ficam de fora.
 */
@RestController
@RequestMapping("/api/v1/admin")
class OverviewController {

    private static final ZoneId ZONE = ZoneId.of("America/Sao_Paulo");
    private static final String TZ = "'America/Sao_Paulo'";

    record Customers(long total, long active, long newInPeriod) {}

    record Accounts(long active, long blocked, long closed, BigDecimal totalBalance) {}

    record Day(LocalDate date, long count, BigDecimal volume) {}

    record ByType(String type, long count, BigDecimal volume) {}

    record Activity(List<Day> days, List<ByType> byType, long totalCount, BigDecimal totalVolume) {}

    record Credit(long cards, BigDecimal totalLimit, BigDecimal used, long overdueInvoices, BigDecimal overdueAmount, BigDecimal lateCharges) {}

    record Security(long failedLogins24h, long accessDenied24h, long outboxPending) {}

    record Overview(Instant generatedAt, int days, Customers customers, Accounts accounts, Activity activity, Credit credit, Security security) {}

    private final JdbcTemplate jdbc;

    OverviewController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Volume = dinheiro que saiu (lançamentos de débito concluídos): cada operação é contada uma vez. */
    @GetMapping("/overview")
    Overview overview(@RequestParam(defaultValue = "30") int days) {
        if (days < 7 || days > 90) {
            throw new InvalidValueException("days must be between 7 and 90");
        }
        Instant since = LocalDate.now(ZONE).minusDays(days - 1L).atStartOfDay(ZONE).toInstant();
        return new Overview(Instant.now(), days, customers(since), accounts(), activity(days, since), credit(), security());
    }

    private Customers customers(Instant since) {
        return jdbc.queryForObject("select count(*), count(*) filter (where status = 'ACTIVE'), count(*) filter (where created_at >= ?) from customers",
                (r, i) -> new Customers(r.getLong(1), r.getLong(2), r.getLong(3)), Timestamp.from(since));
    }

    private Accounts accounts() {
        return jdbc.queryForObject("select count(*) filter (where status = 'ACTIVE'), count(*) filter (where status = 'BLOCKED'),"
                + " count(*) filter (where status = 'CLOSED'), coalesce(sum(balance) filter (where status <> 'CLOSED' and currency = 'BRL'), 0)"
                + " from accounts", (r, i) -> new Accounts(r.getLong(1), r.getLong(2), r.getLong(3), r.getBigDecimal(4)));
    }

    private Activity activity(int days, Instant since) {
        Timestamp from = Timestamp.from(since);
        Map<LocalDate, Day> found = new HashMap<>();
        jdbc.query("select (created_at at time zone " + TZ + ")::date d, count(*), coalesce(sum(amount), 0) from transactions"
                + " where created_at >= ? and direction = 'DEBIT' and status = 'COMPLETED' and currency = 'BRL' group by 1",
                r -> {
                    LocalDate date = r.getDate(1).toLocalDate();
                    found.put(date, new Day(date, r.getLong(2), r.getBigDecimal(3)));
                }, from);
        List<Day> series = new ArrayList<>();
        LocalDate first = since.atZone(ZONE).toLocalDate();
        for (int i = 0; i < days; i++) {
            LocalDate d = first.plusDays(i);
            series.add(found.getOrDefault(d, new Day(d, 0, BigDecimal.ZERO)));
        }
        List<ByType> byType = jdbc.query("select type, count(*), coalesce(sum(amount), 0) from transactions where created_at >= ?"
                + " and direction = 'DEBIT' and status = 'COMPLETED' and currency = 'BRL' group by type order by 3 desc",
                (r, i) -> new ByType(r.getString(1), r.getLong(2), r.getBigDecimal(3)), from);
        long count = series.stream().mapToLong(Day::count).sum();
        BigDecimal volume = series.stream().map(Day::volume).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new Activity(series, byType, count, volume);
    }

    /** Dívida = parcelas + encargos - pagamentos por fatura (nunca negativa); vencida = passou do dia de vencimento com saldo. */
    private Credit credit() {
        String overdue = "open > 0 and to_date(m || '-01', 'YYYY-MM-DD') + (t.due_day - 1) < (now() at time zone " + TZ + ")::date";
        String invoices = "with inst as (select card_id, invoice_month m, sum(amount) a from credit_installments group by 1, 2),"
                + " chg as (select card_id, invoice_month m, sum(amount) a from credit_charges group by 1, 2),"
                + " pay as (select card_id, invoice_month m, sum(amount) a from credit_payments group by 1, 2),"
                + " keys as (select card_id, m from inst union select card_id, m from chg union select card_id, m from pay),"
                + " inv as (select k.card_id, k.m, coalesce(i.a, 0) + coalesce(c.a, 0) - coalesce(p.a, 0) open from keys k"
                + "   left join inst i on i.card_id = k.card_id and i.m = k.m left join chg c on c.card_id = k.card_id and c.m = k.m"
                + "   left join pay p on p.card_id = k.card_id and p.m = k.m)"
                + " select coalesce(sum(greatest(open, 0)), 0), count(*) filter (where " + overdue + "),"
                + " coalesce(sum(open) filter (where " + overdue + "), 0)"
                + " from inv join credit_terms t on t.card_id = inv.card_id";
        Object[] owed = jdbc.queryForObject(invoices, (r, i) -> new Object[] {r.getBigDecimal(1), r.getLong(2), r.getBigDecimal(3)});
        Object[] cards = jdbc.queryForObject("select count(*), coalesce(sum(t.credit_limit), 0) from credit_terms t join cards c on c.id = t.card_id"
                + " where c.status <> 'CANCELED'", (r, i) -> new Object[] {r.getLong(1), r.getBigDecimal(2)});
        BigDecimal charges = jdbc.queryForObject("select coalesce(sum(amount), 0) from credit_charges", BigDecimal.class);
        return new Credit((Long) cards[0], (BigDecimal) cards[1], (BigDecimal) owed[0], (Long) owed[1], (BigDecimal) owed[2], charges);
    }

    private Security security() {
        Timestamp from = Timestamp.from(Instant.now().minusSeconds(86_400));
        Long failed = jdbc.queryForObject("select count(*) from audit_logs where occurred_at >= ? and event = 'LOGIN_FAILED'", Long.class, from);
        Long denied = jdbc.queryForObject("select count(*) from audit_logs where occurred_at >= ? and event = 'ACCESS_DENIED'", Long.class, from);
        Long pending = jdbc.queryForObject("select count(*) from outbox_events where published_at is null", Long.class);
        return new Security(failed, denied, pending);
    }
}
