package com.securebank.card.application;

import com.securebank.card.domain.Card;
import com.securebank.card.domain.CardKind;
import com.securebank.card.domain.CardPurchase;
import com.securebank.shared.application.PageResult;
import com.securebank.shared.domain.AccountId;
import com.securebank.shared.domain.CardId;
import com.securebank.shared.domain.CustomerId;
import java.util.List;
import java.util.Optional;

public interface CardRepository {

    Optional<Card> findById(CardId id);

    /** Cartões do cliente que não foram cancelados, do mais antigo para o mais novo. */
    List<Card> findOpenByCustomer(CustomerId customerId);

    /** Cartões ativos ou bloqueados da conta, do tipo dado (cancelado não conta para o limite). */
    long countOpenByAccount(AccountId accountId, CardKind kind);

    /** Grava um cartão novo junto com o número cifrado e o hash do número (único: dois cartões não repetem número). */
    void insert(Card card, String encryptedNumber, String numberHash);

    /** Atualiza o estado do cartão (a versão otimista é conferida no flush). */
    void save(Card card);

    /** O número completo, cifrado, para mostrar na tela de detalhes. */
    Optional<String> encryptedNumber(CardId id);

    void save(CardPurchase purchase);

    PageResult<CardPurchase> findPurchases(CardId cardId, int page, int size);
}
