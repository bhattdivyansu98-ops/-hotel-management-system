package com.divyanshu.hotel.exception;

/** Bad credentials or a missing/expired session; maps to HTTP 401. */
public class AuthenticationException extends RuntimeException {
    public AuthenticationException(String message) {
        super(message);
    }
}
