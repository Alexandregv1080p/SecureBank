package com.securebank.shared.domain;

import static com.securebank.DomainFixtures.NOW;
import static com.securebank.DomainFixtures.VALID_CPF;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.securebank.customer.domain.Cpf;
import com.securebank.customer.domain.Customer;
import com.securebank.customer.domain.Phone;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Regras de texto digitado: e-mail, nome e texto livre. Os mesmos casos existem no web e no Android. */
class InputRulesTest {

    @ParameterizedTest
    @ValueSource(strings = {"ana@example.com", "ana.souza+banco@mail.example.com.br", "a_b-c@x1.io", "ANA@Example.COM"})
    void acceptsRealEmails(String email) {
        assertThatCode(() -> Email.forInput(email)).doesNotThrowAnyException();
    }

    @ParameterizedTest
    @ValueSource(strings = {"a@b", "a b@x.com", "a@@x.com", "user@x..com", ".user@x.com", "user.@x.com", "us..er@x.com",
            "user@-x.com", "user@x-.com", "user@x.c", "user@x.123", "ÁÉ@x.com", "user@x_y.com", "@x.com", "user@.com"})
    void refusesEmailsThatCannotBeDelivered(String email) {
        assertThatThrownBy(() -> Email.forInput(email)).isInstanceOf(InvalidValueException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"user@x..com", ".user@x.com"})
    void anEmailAlreadyStoredStillLoads(String stored) {
        assertThat(new Email(stored).value()).isEqualTo(stored); // o construtor segue tolerante: não tranca contas antigas
    }

    @ParameterizedTest
    @ValueSource(strings = {"Maria da Silva", "Ana D'Ávila-Souza", "Ana D’Ávila", "José Álvaro Jr.", "Дмитрий Иванов", "Li Wei"})
    void acceptsRealNames(String name) {
        assertThat(register(name).name()).isEqualTo(name);
    }

    @ParameterizedTest
    @ValueSource(strings = {"Maria", "Maria 123 Silva", "Maria<script> Silva", "Maria Silva ☃", "Maria_Silva Souza", "M@ria Silva"})
    void refusesNamesWithOtherThanLettersOrWithoutSurname(String name) {
        assertThatThrownBy(() -> register(name)).isInstanceOf(InvalidValueException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"  Maria   da    Silva  ", "Maria\tda\nSilva"})
    void collapsesWhitespaceInsideTheName(String name) {
        assertThat(register(name).name()).isEqualTo("Maria da Silva");
    }

    @ParameterizedTest
    @ValueSource(strings = {"a\nb", "a\rb", "a\u0007b", "a\u0000b", "a b", "a‮b", "a​b"})
    void freeTextRefusesLineBreaksControlAndDirectionMarks(String text) {
        assertThatThrownBy(() -> Text.optional(text)).isInstanceOf(InvalidValueException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"almoço", "Pix da pizza 🍕", "R$ 50,00 - parte 1/2", "<b>x</b>", "👨‍👩‍👧"})
    void freeTextKeepsAccentsEmojisAndSymbols(String text) {
        assertThat(Text.optional("  " + text + "  ")).isEqualTo(text);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "\t "})
    void blankFreeTextBecomesNull(String text) {
        assertThat(Text.optional(text)).isNull();
    }

    private static Customer register(String name) {
        return Customer.register(name, new Cpf(VALID_CPF), new Email("a@b.co"), Phone.forContact("+5511999990001"), NOW);
    }
}
