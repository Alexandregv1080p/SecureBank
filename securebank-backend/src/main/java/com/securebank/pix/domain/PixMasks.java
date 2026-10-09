package com.securebank.pix.domain;

/** Como o nome do destinatário aparece antes de confirmar: o suficiente para reconhecer, não para coletar dados. */
public final class PixMasks {

    private PixMasks() {}

    /** "Ana Souza Lima" -> "Ana S*** L***": o primeiro nome inteiro e só a inicial dos demais. */
    public static String name(String fullName) {
        return com.securebank.shared.domain.Masks.name(fullName);
    }
}
