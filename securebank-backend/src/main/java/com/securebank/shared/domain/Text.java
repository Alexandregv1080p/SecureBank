package com.securebank.shared.domain;

/** Texto livre digitado pelo cliente (descrição, mensagem, nome de porquinho): sem caracteres de controle nem de direção. */
public final class Text {

    private Text() {}

    /** Texto sem as pontas em branco, ou null se vazio. Recusa quebra de linha, controle e marcas de direção (U+202E e afins). */
    public static String optional(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String text = raw.trim();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            int type = Character.getType(c);
            boolean bad = Character.isISOControl(c) || type == Character.LINE_SEPARATOR || type == Character.PARAGRAPH_SEPARATOR
                    || (type == Character.FORMAT && c != '‍' && c != '‌');
            if (bad) {
                throw new InvalidValueException("Text must not contain line breaks or control characters");
            }
        }
        return text;
    }
}
