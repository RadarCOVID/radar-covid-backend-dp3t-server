package org.dpppt.backend.sdk.ws.handler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.validation.ConstraintViolationException;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;


@RestControllerAdvice
public class DppptExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(DppptExceptionHandler.class);

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public void unknownException(Exception ex, WebRequest wr) {
        logger.error("Unable to handle {}", wr.getDescription(false), ex);
    }

    @ExceptionHandler({
            MethodArgumentNotValidException.class,
            ConstraintViolationException.class
    })
    public ResponseEntity<?> handleValidationExceptions(Exception ex, WebRequest wr) {
        logger.error("Validation failed {}", wr.getDescription(false), ex);
        return ResponseEntity.badRequest().build();
    }

    @ExceptionHandler({
            HttpMessageNotReadableException.class,
            ServletRequestBindingException.class
    })
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public void bindingExceptions(Exception ex, WebRequest wr) {
        logger.error("Binding failed {}", wr.getDescription(false), ex);
    }

}