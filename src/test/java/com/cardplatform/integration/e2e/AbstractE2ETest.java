package com.cardplatform.integration.e2e;

import com.cardplatform.domain.account.model.Account;
import com.cardplatform.domain.account.repository.AccountRepository;
import com.cardplatform.domain.card.repository.CardControlsRepository;
import com.cardplatform.domain.card.repository.CardRepository;
import com.cardplatform.domain.ledger.repository.LedgerRepository;
import com.cardplatform.domain.transaction.repository.AuthorizationRepository;
import com.cardplatform.domain.transaction.repository.TransactionHoldRepository;
import com.cardplatform.domain.transaction.repository.TransactionRepository;
import com.cardplatform.common.money.MonetaryAmount;
import com.cardplatform.infrastructure.persistence.jpa.SpringDataAccountRepository;
import com.cardplatform.infrastructure.persistence.jpa.SpringDataJournalEntryRepository;
import com.cardplatform.infrastructure.persistence.jpa.SpringDataLedgerPostingRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import java.math.BigDecimal;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public abstract class AbstractE2ETest {

    @LocalServerPort
    protected int port;

    @Autowired
    protected TestRestTemplate restTemplate;

    @Autowired
    protected AccountRepository accountRepository;

    @Autowired
    protected CardRepository cardRepository;

    @Autowired
    protected CardControlsRepository cardControlsRepository;

    @Autowired
    protected AuthorizationRepository authorizationRepository;

    @Autowired
    protected TransactionRepository transactionRepository;

    @Autowired
    protected TransactionHoldRepository transactionHoldRepository;

    @Autowired
    protected LedgerRepository ledgerRepository;

    @Autowired
    protected SpringDataAccountRepository springDataAccountRepository;

    @Autowired
    protected SpringDataJournalEntryRepository springDataJournalEntryRepository;

    @Autowired
    protected SpringDataLedgerPostingRepository springDataLedgerPostingRepository;

    protected String baseUrl(String path) {
        return "http://localhost:" + port + path;
    }

    protected HttpHeaders createHeaders(String idempotencyKey) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            headers.set("Idempotency-Key", idempotencyKey);
        }
        return headers;
    }

    protected Account createFundedAccount(String accountNumber, String currency, double amount) {
        MonetaryAmount balance = MonetaryAmount.of(BigDecimal.valueOf(amount), currency);
        Account account = Account.create(accountNumber, currency, balance);
        return accountRepository.save(account);
    }
}
