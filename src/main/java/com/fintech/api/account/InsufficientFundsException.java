package com.fintech.api.account;

import java.math.BigDecimal;

public class InsufficientFundsException extends RuntimeException {

    public InsufficientFundsException(String accountNumber, BigDecimal balance, BigDecimal requested) {
        super("Account %s has insufficient funds. Balance: %s, Requested: %s"
                .formatted(accountNumber, balance, requested));
    }
}
