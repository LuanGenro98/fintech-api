package com.fintech.api.account;

public class DuplicateAccountException extends RuntimeException {

    public DuplicateAccountException(String accountNumber) {
        super("Account number already exists: " + accountNumber);
    }
}
