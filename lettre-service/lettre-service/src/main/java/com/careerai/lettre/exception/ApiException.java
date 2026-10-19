package com.careerai.lettre.exception;

import org.springframework.http.HttpStatus;

public class ApiException extends RuntimeException {
    private final HttpStatus status;

    public ApiException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus getStatus() { return status; }

    public static ApiException badRequest(String m)      { return new ApiException(HttpStatus.BAD_REQUEST, m); }
    public static ApiException notFound(String m)        { return new ApiException(HttpStatus.NOT_FOUND, m); }
    public static ApiException tooManyRequests(String m) { return new ApiException(HttpStatus.TOO_MANY_REQUESTS, m); }
    public static ApiException unavailable(String m)     { return new ApiException(HttpStatus.SERVICE_UNAVAILABLE, m); }
    public static ApiException badGateway(String m)      { return new ApiException(HttpStatus.BAD_GATEWAY, m); }
    public static ApiException gatewayTimeout(String m)  { return new ApiException(HttpStatus.GATEWAY_TIMEOUT, m); }
    public static ApiException internal(String m)        { return new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, m); }
}
