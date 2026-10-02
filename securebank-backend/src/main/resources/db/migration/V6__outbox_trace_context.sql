-- Fase 10: contexto de trace (W3C traceparent) da requisição que gerou o evento, para o trace continuar pelo Kafka.
ALTER TABLE outbox_events ADD COLUMN traceparent VARCHAR(64);
