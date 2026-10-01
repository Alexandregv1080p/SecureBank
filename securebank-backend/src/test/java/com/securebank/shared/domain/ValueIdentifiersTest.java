package com.securebank.shared.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ValueIdentifiersTest {

    @ParameterizedTest
    @ValueSource(strings = {"abc", "short-1", "has space in it", "ação-com-acento-123"})
    void rejectsMalformedIdempotencyKeys(String value) {
        assertThatThrownBy(() -> new IdempotencyKey(value)).isInstanceOf(InvalidValueException.class);
    }

    @Test
    void acceptsUuidShapedIdempotencyKey() {
        assertThat(new IdempotencyKey(UUID.randomUUID().toString())).isNotNull();
    }

    @Test
    void typedIdsRoundTripAndAreNotInterchangeable() {
        AccountId id = AccountId.newId();
        assertThat(AccountId.of(id.toString())).isEqualTo(id);
        assertThat((Object) id).isNotEqualTo(new CustomerId(id.value()));
        assertThatThrownBy(() -> AccountId.of("not-a-uuid")).isInstanceOf(InvalidValueException.class);
    }
}
