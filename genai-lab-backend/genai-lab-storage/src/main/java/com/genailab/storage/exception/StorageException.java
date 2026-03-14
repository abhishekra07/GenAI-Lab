package com.genailab.storage.exception;

/**
 * Thrown when a storage operation fails.
 *
 * <p>Unchecked so callers don't need to declare it in throws clauses,
 * but descriptive enough to surface useful error messages.
 */
public class StorageException extends RuntimeException {

    public StorageException(String message) {
        super(message);
    }

    public StorageException(String message, Throwable cause) {
        super(message, cause);
    }
}