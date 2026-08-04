package it.pegasopw.grapehealth.api.exception;

import java.time.Instant;

public record ErroreResponse(Instant timestamp, int status, String errore, String messaggio, String path) {
    public static ErroreResponse of(int status, String errore, String messaggio, String path) {
        return new ErroreResponse(Instant.now(), status, errore, messaggio, path);
    }
}