package it.pegasopw.grapehealth.api.exception;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ParametriNonValidiException.class)
    public ResponseEntity<ErroreResponse> gestisciParametriNonValidi(ParametriNonValidiException ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ErroreResponse.of(
                HttpStatus.BAD_REQUEST.value(), "Parametri non validi", ex.getMessage(), request.getRequestURI()));
    }

    @ExceptionHandler(RisorsaNonTrovataException.class)
    public ResponseEntity<ErroreResponse> gestisciRisorsaNonTrovata(RisorsaNonTrovataException ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ErroreResponse.of(
                HttpStatus.NOT_FOUND.value(), "Risorsa non trovata", ex.getMessage(), request.getRequestURI()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErroreResponse> gestisciErroreGenerico(Exception ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ErroreResponse.of(
                HttpStatus.INTERNAL_SERVER_ERROR.value(), "Errore interno",
                "Si e' verificato un errore imprevisto durante l'elaborazione della richiesta.",
                request.getRequestURI()));
    }
}