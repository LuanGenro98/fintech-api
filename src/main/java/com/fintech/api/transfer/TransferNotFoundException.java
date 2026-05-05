package com.fintech.api.transfer;

public class TransferNotFoundException extends RuntimeException {
    public TransferNotFoundException(Long id) {
        super("Transfer not found with id: " + id);
    }
}
