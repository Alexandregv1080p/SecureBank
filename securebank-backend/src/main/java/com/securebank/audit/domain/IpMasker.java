package com.securebank.audit.domain;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.regex.Pattern;

/**
 * Anonimiza o IP antes de gravar: IPv4 perde o último octeto (/24); IPv6 mantém só os 48 bits iniciais (/48).
 * O IPv6 é interpretado (não cortado por texto), porque a notação comprimida ("::1") esconde quantos grupos há.
 */
public final class IpMasker {

    private static final Pattern IPV6_LITERAL = Pattern.compile("[0-9a-fA-F:.]+");

    private IpMasker() {}

    public static String mask(String ip) {
        if (ip == null || ip.isBlank()) {
            return null;
        }
        String value = ip.trim();
        if (value.contains(":") && IPV6_LITERAL.matcher(value).matches()) {
            return maskIpv6(value);
        }
        int last = value.lastIndexOf('.');
        return last < 0 || !value.chars().allMatch(c -> Character.isDigit(c) || c == '.')
                ? "masked"
                : value.substring(0, last) + ".0";
    }

    private static String maskIpv6(String literal) {
        try {
            // só literais chegam aqui (regex acima), então não há consulta de DNS
            InetAddress address = InetAddress.getByName(literal);
            if (address instanceof Inet4Address) { // IPv6 mapeado de IPv4 (::ffff:1.2.3.4)
                return mask(address.getHostAddress());
            }
            byte[] b = address.getAddress();
            return String.format("%x:%x:%x::", ((b[0] & 0xFF) << 8) | (b[1] & 0xFF),
                    ((b[2] & 0xFF) << 8) | (b[3] & 0xFF), ((b[4] & 0xFF) << 8) | (b[5] & 0xFF));
        } catch (UnknownHostException e) {
            return "masked";
        }
    }
}
