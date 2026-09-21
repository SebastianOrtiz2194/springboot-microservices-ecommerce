package com.ecommerce.user.exception;

/** Thrown when registration is attempted with an email address that already exists. */
public class EmailAlreadyRegisteredException extends RuntimeException {

    /**
     * Creates the exception for the conflicting email address.
     *
     * @param email the email that is already registered
     */
    public EmailAlreadyRegisteredException(String email) {
        super("Email already registered: " + email);
    }
}
