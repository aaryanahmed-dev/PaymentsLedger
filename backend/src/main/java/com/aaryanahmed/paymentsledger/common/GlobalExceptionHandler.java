package com.aaryanahmed.paymentsledger.common;

import com.aaryanahmed.paymentsledger.account.AccountNotFoundException;
import com.aaryanahmed.paymentsledger.account.InsufficientFundsException;
import com.aaryanahmed.paymentsledger.idempotency.IdempotencyKeyConflictException;
import com.aaryanahmed.paymentsledger.idempotency.InvalidIdempotencyKeyException;
import com.aaryanahmed.paymentsledger.transfer.CurrencyMismatchException;
import com.aaryanahmed.paymentsledger.transfer.InvalidTransferException;
import com.aaryanahmed.paymentsledger.transfer.TransferNotFoundException;
import com.aaryanahmed.paymentsledger.transfer.TransferNotReversibleException;
import com.aaryanahmed.paymentsledger.transfer.UnsupportedCurrencyException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Turns exceptions into RFC 9457 "problem detail" JSON responses.
 * Extending ResponseEntityExceptionHandler makes Spring's own errors
 * (malformed JSON, failed validation, wrong HTTP method, ...) use the same format.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler({AccountNotFoundException.class, TransferNotFoundException.class})
    public ProblemDetail handleNotFound(RuntimeException ex)
    {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    // 422: the request is well-formed, but a business rule says no.
    @ExceptionHandler({InsufficientFundsException.class, CurrencyMismatchException.class, UnsupportedCurrencyException.class})
    public ProblemDetail handleBusinessRuleViolation(RuntimeException ex)
    {
        return ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage());
    }

    @ExceptionHandler({InvalidTransferException.class, InvalidIdempotencyKeyException.class})
    public ProblemDetail handleInvalidRequest(RuntimeException ex)
    {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    // 409: the request conflicts with something that already happened.
    @ExceptionHandler({IdempotencyKeyConflictException.class, TransferNotReversibleException.class})
    public ProblemDetail handleConflict(RuntimeException ex)
    {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
    }

    // Bean Validation failed on a @Valid request body: add which fields were wrong.
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
                                                                  HttpHeaders headers,
                                                                  HttpStatusCode status,
                                                                  WebRequest request)
    {
        Map<String, String> errors = new LinkedHashMap<>();
        for (FieldError fieldError : ex.getBindingResult().getFieldErrors()) {
            errors.put(fieldError.getField(), fieldError.getDefaultMessage());
        }
        ProblemDetail problem = ex.getBody();
        problem.setProperty("errors", errors);
        return handleExceptionInternal(ex, problem, headers, status, request);
    }
}
