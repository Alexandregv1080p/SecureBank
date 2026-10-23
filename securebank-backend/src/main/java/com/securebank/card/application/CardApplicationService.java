package com.securebank.card.application;

import com.securebank.account.application.AccountApplicationService;
import com.securebank.account.application.AccountRepository;
import com.securebank.account.domain.Account;
import com.securebank.account.domain.AccountStatus;
import com.securebank.audit.application.AuditEntry;
import com.securebank.audit.application.AuditService;
import com.securebank.audit.domain.AuditEvent;
import com.securebank.card.domain.Card;
import com.securebank.card.domain.CardNumber;
import com.securebank.card.domain.CardPurchase;
import com.securebank.card.domain.CardPurchaseService;
import com.securebank.card.domain.CardStatus;
import com.securebank.customer.application.CustomerApplicationService;
import com.securebank.limit.application.LimitUsage;
import com.securebank.limit.domain.LimitType;
import com.securebank.outbox.application.OutboxService;
import com.securebank.shared.application.ApplicationException;
import com.securebank.shared.application.BankTime;
import com.securebank.shared.application.PageResult;
import com.securebank.shared.application.TransactionalRetry;
import com.securebank.shared.domain.AccountId;
import com.securebank.shared.domain.CardId;
import com.securebank.shared.domain.CustomerId;
import com.securebank.shared.domain.DomainException;
import com.securebank.shared.domain.Money;
import com.securebank.transaction.application.TransactionRepository;
import com.securebank.transaction.domain.Transaction;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Casos de uso do cartão de débito virtual. Cartão inexistente e de outro dono são indistinguíveis (404). Mostrar o
 * número completo é um ato à parte (e auditado): a listagem só devolve os 4 últimos dígitos.
 */
@Service
@Transactional
public class CardApplicationService {

    public static final int MAX_OPEN_PER_ACCOUNT = 3;

    /** Número, validade e CVV, só na tela de detalhes. */
    public record Details(String number, YearMonth expiry, String cvv) {}

    private final CardPurchaseService domain = new CardPurchaseService();

    private final CardRepository cards;
    private final CardSecrets secrets;
    private final AccountApplicationService accountService;
    private final AccountRepository accounts;
    private final CustomerApplicationService customers;
    private final LimitUsage limitUsage;
    private final TransactionRepository transactions;
    private final BankTime time;
    private final AuditService audit;
    private final OutboxService outbox;
    private final TransactionalRetry retry;

    public CardApplicationService(CardRepository cards, CardSecrets secrets, AccountApplicationService accountService,
            AccountRepository accounts, CustomerApplicationService customers, LimitUsage limitUsage,
            TransactionRepository transactions, BankTime time, AuditService audit, OutboxService outbox,
            TransactionalRetry retry) {
        this.cards = cards;
        this.secrets = secrets;
        this.accountService = accountService;
        this.accounts = accounts;
        this.customers = customers;
        this.limitUsage = limitUsage;
        this.transactions = transactions;
        this.time = time;
        this.audit = audit;
        this.outbox = outbox;
        this.retry = retry;
    }

    public Card issue(CustomerId requester, AccountId accountId) {
        customers.requireActive(requester);
        Account account = accountService.findOwned(requester, accountId);
        if (account.status() != AccountStatus.ACTIVE) {
            throw ApplicationException.unprocessable("ACCOUNT_NOT_ACTIVE", "The account is not active");
        }
        if (cards.countOpenByAccount(account.id()) >= MAX_OPEN_PER_ACCOUNT) {
            throw ApplicationException.unprocessable("CARD_LIMIT_REACHED",
                    "An account can have at most " + MAX_OPEN_PER_ACCOUNT + " cards. Cancel one to issue another");
        }
        String number = CardNumber.generate();
        Card card = Card.issue(requester, account.id(), customers.get(requester).name(), number, time.today(), time.now());
        cards.insert(card, secrets.encrypt(number), secrets.fingerprint(number));
        audit.record(AuditEntry.of(AuditEvent.CARD_ISSUED).account(account.id()).detail(card.id().toString()));
        return card;
    }

    @Transactional(readOnly = true)
    public List<Card> list(CustomerId requester) {
        return cards.findOpenByCustomer(requester);
    }

    @Transactional(readOnly = true)
    public Card get(CustomerId requester, CardId id) {
        return findOwned(requester, id);
    }

    /** Número completo e CVV: cada consulta fica na auditoria. Cartão cancelado não mostra mais nada. */
    public Details details(CustomerId requester, CardId id) {
        customers.requireActive(requester);
        Card card = findOwned(requester, id);
        if (card.status() == CardStatus.CANCELED) {
            throw ApplicationException.unprocessable("CARD_NOT_USABLE", "This card is canceled");
        }
        String number = secrets.decrypt(cards.encryptedNumber(id).orElseThrow(() -> ApplicationException.notFound("Card")));
        audit.record(AuditEntry.of(AuditEvent.CARD_REVEALED).account(card.accountId()).detail(card.id().toString()));
        return new Details(number, card.expiry(), secrets.cvv(number, card.expiry()));
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public Card block(CustomerId requester, CardId id) {
        return retry.execute(() -> change(requester, id, AuditEvent.CARD_BLOCKED, "CardBlocked", Card::block));
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public Card unblock(CustomerId requester, CardId id) {
        return retry.execute(() -> change(requester, id, AuditEvent.CARD_UNBLOCKED, "CardUnblocked", Card::unblock));
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public Card cancel(CustomerId requester, CardId id) {
        return retry.execute(() -> change(requester, id, AuditEvent.CARD_CANCELED, "CardCanceled", Card::cancel));
    }

    private Card change(CustomerId requester, CardId id, AuditEvent event, String outboxType,
            java.util.function.BiConsumer<Card, Instant> action) {
        customers.requireActive(requester);
        Card card = findOwned(requester, id);
        action.accept(card, time.now());
        cards.save(card);
        audit.record(AuditEntry.of(event).account(card.accountId()).detail(card.id().toString()));
        outbox.record(outboxType, "Card", card.id().toString(),
                Map.of("cardId", card.id().toString(), "accountId", card.accountId().toString(), "last4", card.last4()));
        return card;
    }

    /** Mesmo desenho do pagamento: sucesso auditado na transação; falha auditada em transação à parte. */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public CardPurchase purchase(CustomerId requester, CardId id, BigDecimal amount, String merchant) {
        AccountId accountId = null;
        try {
            Card card = cards.findById(id).filter(c -> c.customerId().equals(requester)).orElse(null);
            accountId = card == null ? null : card.accountId();
            return retry.execute(() -> doPurchase(requester, id, amount, merchant));
        } catch (DomainException e) {
            failed(accountId, e.code());
            throw e;
        } catch (ApplicationException e) {
            failed(accountId, e.code());
            throw e;
        }
    }

    private void failed(AccountId accountId, String code) {
        if (accountId != null) {
            audit.recordIndependently(AuditEntry.of(AuditEvent.CARD_PURCHASE_FAILED).account(accountId).detail(code));
        }
    }

    private CardPurchase doPurchase(CustomerId requester, CardId id, BigDecimal amount, String merchant) {
        customers.requireActive(requester);
        Card card = findOwned(requester, id);
        Account account = accountService.findOwned(requester, card.accountId());
        Instant now = time.now();
        Money money = new Money(amount, account.balance().currency());
        LimitUsage.Status limit = limitUsage.of(account.id(), LimitType.CARD, money.currency());

        // valida o estabelecimento antes de mexer na conta: o lançamento só existe se a compra existir
        CardPurchase.validateMerchant(merchant);
        Transaction transaction = domain.purchase(card, account, limit.limit(), limit.usedToday(), money, time.today(), now,
                card.id().toString());
        CardPurchase purchase = CardPurchase.create(card, money, merchant, transaction.id(), now);

        accounts.save(account);
        transactions.save(transaction);
        cards.save(purchase);
        audit.record(AuditEntry.of(AuditEvent.CARD_PURCHASE).account(account.id()).transaction(transaction.id())
                .detail(card.id().toString()));
        outbox.record("CardPurchaseCompleted", "Card", card.id().toString(), Map.of(
                "cardId", card.id().toString(), "accountId", account.id().toString(), "last4", card.last4(),
                "merchant", purchase.merchant(), "amount", money.amount().toPlainString(),
                "currency", money.currency().getCurrencyCode()));
        return purchase;
    }

    @Transactional(readOnly = true)
    public PageResult<CardPurchase> purchases(CustomerId requester, CardId id, int page, int size) {
        PageResult.validate(page, size);
        findOwned(requester, id);
        return cards.findPurchases(id, page, size);
    }

    private Card findOwned(CustomerId requester, CardId id) {
        return cards.findById(id).filter(c -> c.customerId().equals(requester))
                .orElseThrow(() -> ApplicationException.notFound("Card"));
    }
}
