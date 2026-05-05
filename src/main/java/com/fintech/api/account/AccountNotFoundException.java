package com.fintech.api.account;

public class AccountNotFoundException extends RuntimeException {

    public AccountNotFoundException(Long id) {
        super("Account not found with id: " + id);
    }

    public AccountNotFoundException(String accountNumber) {
        super("Account not found: " + accountNumber);
    }
}
