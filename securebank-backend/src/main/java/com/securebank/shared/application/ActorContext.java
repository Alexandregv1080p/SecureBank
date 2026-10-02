package com.securebank.shared.application;

/** Porta: a camada web/segurança informa quem chama; os casos de uso e a auditoria só leem. */
public interface ActorContext {

    Actor current();
}
