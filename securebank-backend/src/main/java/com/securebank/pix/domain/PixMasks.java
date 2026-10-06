package com.securebank.pix.domain;

/** Como o nome do destinatário aparece antes de confirmar: o suficiente para reconhecer, não para coletar dados. */
public final class PixMasks {

    private PixMasks() {}

    /** "Ana Souza Lima" -> "Ana S*** L***": o primeiro nome inteiro e só a inicial dos demais. */
    public static String name(String fullName) {
        if (fullName == null || fullName.isBlank()) {
            return "";
        }
        String[] parts = fullName.trim().split("\\s+");
        StringBuilder masked = new StringBuilder(parts[0]);
        for (int i = 1; i < parts.length; i++) {
            masked.append(' ').append(parts[i].charAt(0)).append("***");
        }
        return masked.toString();
    }
}
