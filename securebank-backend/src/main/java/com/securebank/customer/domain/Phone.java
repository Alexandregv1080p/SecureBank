package com.securebank.customer.domain;

import com.securebank.shared.domain.InvalidValueException;
import java.util.Set;
import java.util.regex.Pattern;

/** Telefone em E.164 (ex.: +5511999998888). */
public record Phone(String value) {

    private static final Pattern E164 = Pattern.compile("\\+[1-9]\\d{9,14}");

    public Phone {
        if (value == null || !E164.matcher(value).matches()) {
            throw new InvalidValueException("Phone must be in E.164 format, e.g. +5511999998888");
        }
    }

    /** DDDs que existem no Brasil. */
    private static final Set<String> DDD = Set.of("11", "12", "13", "14", "15", "16", "17", "18", "19", "21", "22",
            "24", "27", "28", "31", "32", "33", "34", "35", "37", "38", "41", "42", "43", "44", "45", "46", "47", "48",
            "49", "51", "53", "54", "55", "61", "62", "63", "64", "65", "66", "67", "68", "69", "71", "73", "74", "75",
            "77", "79", "81", "82", "83", "84", "85", "86", "87", "88", "89", "91", "92", "93", "94", "95", "96", "97",
            "98", "99");

    /**
     * Telefone para cadastro ou atualização: além do E.164, número brasileiro (+55) precisa de DDD que exista, celular
     * de 11 dígitos começando com 9 e fixo de 10 começando com 2 a 5. Fica fora do construtor para que clientes antigos,
     * já gravados, continuem carregando.
     */
    public static Phone forContact(String raw) {
        Phone phone = new Phone(raw);
        if (phone.value.startsWith("+55")) {
            String national = phone.value.substring(3);
            boolean ok = (national.length() == 10 || national.length() == 11) && DDD.contains(national.substring(0, 2))
                    && (national.length() == 11 ? national.charAt(2) == '9' : "2345".indexOf(national.charAt(2)) >= 0);
            if (!ok) {
                throw new InvalidValueException("Brazilian phone needs a valid area code and 8 or 9 digits");
            }
        }
        return phone;
    }

    @Override
    public String toString() {
        return value;
    }
}
