package id.imron.careervault.controller;

import java.util.Map;

import jakarta.persistence.EntityNotFoundException;
import jakarta.servlet.http.HttpServletRequest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.TypeMismatchException;
import org.springframework.boot.webmvc.autoconfigure.error.ErrorViewResolver;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.ModelAndView;

/**
 * Renders every error with the {@code error/error} view: exceptions from request handling through the handler methods,
 * and errors raised outside it (such as a rejected CSRF token) through {@link ErrorViewResolver}. Messages never
 * contain exception details.
 */
@ControllerAdvice
public class GlobalExceptionHandler implements ErrorViewResolver {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private static final String VIEW = "error/error";

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    ModelAndView uploadTooLarge() {
        return page(HttpStatus.CONTENT_TOO_LARGE);
    }

    @ExceptionHandler(EntityNotFoundException.class)
    ModelAndView notFound() {
        return page(HttpStatus.NOT_FOUND);
    }

    /** The client went away, for example during a download; there is nobody to answer. */
    @ExceptionHandler(AsyncRequestNotUsableException.class)
    void clientDisconnected() {
    }

    /** Request errors keep their own 4xx status; everything else is a server error. */
    @ExceptionHandler(Exception.class)
    ModelAndView unexpected(Exception ex) {
        HttpStatusCode status = HttpStatus.INTERNAL_SERVER_ERROR;
        if (ex instanceof ErrorResponse response) {
            status = response.getStatusCode();
        }
        else if (ex instanceof TypeMismatchException) {
            status = HttpStatus.BAD_REQUEST;
        }
        if (status.is5xxServerError()) {
            log.error("Request failed", ex);
        }
        return page(status);
    }

    @Override
    public ModelAndView resolveErrorView(HttpServletRequest request, HttpStatus status, Map<String, Object> model) {
        return page(status);
    }

    private static ModelAndView page(HttpStatusCode status) {
        ModelAndView page = new ModelAndView(VIEW, Map.of("status", status.value(), "message", message(status)));
        page.setStatus(status);
        return page;
    }

    private static String message(HttpStatusCode status) {
        return switch (status.value()) {
            case 403 -> "This request was refused. Reload the page and try again.";
            case 404 -> "The page or item you were looking for does not exist.";
            case 413 -> "The upload is too large.";
            default -> status.is4xxClientError()
                    ? "The request could not be processed."
                    : "An unexpected error occurred. Please try again.";
        };
    }
}
