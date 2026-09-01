package it.pegasopw.grapehealth.api.exception;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

// Traduce le eccezioni del livello applicativo in risposte JSON coerenti, stesso formato
// ErroreResponse per tutte. In ordine: input che viola un vincolo applicativo (400), risorsa
// richiesta ma inesistente (404), qualunque altro errore non previsto (500, senza riversare
// il messaggio originale nella risposta), parametri di query che Spring non riesce a
// convertire nel tipo atteso (400, con nome parametro/tipo/valore ricevuto nel messaggio).
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

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErroreResponse> gestisciParametroMalformato(MethodArgumentTypeMismatchException ex, HttpServletRequest request) {
        String tipoAtteso = ex.getRequiredType() != null ? ex.getRequiredType().getSimpleName() : "sconosciuto";
        String messaggio = "Il parametro '%s' non e' nel formato atteso (%s): valore ricevuto '%s'."
                .formatted(ex.getName(), tipoAtteso, ex.getValue());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ErroreResponse.of(
                HttpStatus.BAD_REQUEST.value(), "Parametri non validi", messaggio, request.getRequestURI()));
    }


    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErroreResponse> gestisciPercorsoNonMappato(NoResourceFoundException ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ErroreResponse.of(
                HttpStatus.NOT_FOUND.value(), "Risorsa non trovata",
                "Nessun endpoint corrisponde al percorso richiesto.", request.getRequestURI()));
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErroreResponse> gestisciMetodoNonConsentito(HttpRequestMethodNotSupportedException ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).body(ErroreResponse.of(
                HttpStatus.METHOD_NOT_ALLOWED.value(), "Metodo non consentito",
                "Il metodo '%s' non e' supportato su questo percorso.".formatted(ex.getMethod()), request.getRequestURI()));
    }
}