package com.kaushiksridhar.finledger.common;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

@RestControllerAdvice
public class ApiExceptionHandler {

    /** Builds the JSON error body for an ApiException. Also used directly by controllers that add headers. */
    public static ProblemDetail toProblem(ApiException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(ex.getStatus(), ex.getMessage());
        problem.setTitle(ex.getStatus().getReasonPhrase());
        return problem;
    }

    // Our own errors, e.g. "email already registered"
    @ExceptionHandler(ApiException.class)
    public ProblemDetail handleApiException(ApiException ex) {
        return toProblem(ex);
    }

    // @Valid failed: return every bad field with its message so the form can show them
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> errors = new LinkedHashMap<>();
        for (FieldError fieldError : ex.getBindingResult().getFieldErrors()) {
            errors.putIfAbsent(fieldError.getField(), fieldError.getDefaultMessage());
        }

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Some fields are invalid");
        problem.setTitle("Validation failed");
        problem.setProperty("errors", errors);
        return problem;
    }

    // Missing body or broken JSON
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ProblemDetail handleUnreadableBody(HttpMessageNotReadableException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,
                "Request body is missing or is not valid JSON");
        problem.setTitle("Bad Request");
        return problem;
    }

    // An upload with no file attached
    @ExceptionHandler(MissingServletRequestPartException.class)
    public ProblemDetail handleMissingPart(MissingServletRequestPartException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,
                "Choose a file to upload");
        problem.setTitle("Bad Request");
        return problem;
    }

    // An upload bigger than spring.servlet.multipart.max-file-size
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ProblemDetail handleTooLarge(MaxUploadSizeExceededException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.PAYLOAD_TOO_LARGE,
                "The file is larger than 2 MB");
        problem.setTitle("Payload Too Large");
        return problem;
    }
}
