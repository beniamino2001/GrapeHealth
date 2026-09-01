package it.pegasopw.grapehealth.api.exception;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    private HttpServletRequest request(String path) {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRequestURI()).thenReturn(path);
        return request;
    }

    @Test
    void parametriNonValidiRestituisce400() {
        ResponseEntity<ErroreResponse> risposta = handler.gestisciParametriNonValidi(
                new ParametriNonValidiException("id non valido"), request("/api/misurazioni"));

        assertEquals(HttpStatus.BAD_REQUEST, risposta.getStatusCode());
        assertEquals("Parametri non validi", risposta.getBody().errore());
        assertEquals("id non valido", risposta.getBody().messaggio());
    }

    @Test
    void risorsaNonTrovataRestituisce404() {
        ResponseEntity<ErroreResponse> risposta = handler.gestisciRisorsaNonTrovata(
                new RisorsaNonTrovataException("allerta 999 non trovata"), request("/api/raccomandazioni"));

        assertEquals(HttpStatus.NOT_FOUND, risposta.getStatusCode());
        assertEquals("Risorsa non trovata", risposta.getBody().errore());
    }

    @Test
    void parametroMalformatoRestituisce400ConNomeETipoNelMessaggio() {
        MethodArgumentTypeMismatchException ex = mock(MethodArgumentTypeMismatchException.class);
        when(ex.getName()).thenReturn("dal");
        doReturn(java.time.Instant.class).when(ex).getRequiredType();
        when(ex.getValue()).thenReturn("non-una-data");

        ResponseEntity<ErroreResponse> risposta = handler.gestisciParametroMalformato(ex, request("/api/misurazioni"));

        assertEquals(HttpStatus.BAD_REQUEST, risposta.getStatusCode());
        assertTrue(risposta.getBody().messaggio().contains("dal"));
        assertTrue(risposta.getBody().messaggio().contains("Instant"));
        assertTrue(risposta.getBody().messaggio().contains("non-una-data"));
    }

    @Test
    void erroreGenericoNonEspletoDettaglioInterno() {
        ResponseEntity<ErroreResponse> risposta = handler.gestisciErroreGenerico(
                new RuntimeException("dettaglio interno sensibile"), request("/api/allerte"));

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, risposta.getStatusCode());
        assertFalse(risposta.getBody().messaggio().contains("dettaglio interno sensibile"));
    }


    @Test
    void percorsoNonMappatoRestituisce404() {
        NoResourceFoundException ex = mock(NoResourceFoundException.class);

        ResponseEntity<ErroreResponse> risposta = handler.gestisciPercorsoNonMappato(ex, request("/api/percorso-inesistente"));

        assertEquals(HttpStatus.NOT_FOUND, risposta.getStatusCode());
        assertEquals("Risorsa non trovata", risposta.getBody().errore());
    }

    @Test
    void metodoNonSupportatoRestituisce405ConIlMetodoNelMessaggio() {
        HttpRequestMethodNotSupportedException ex = mock(HttpRequestMethodNotSupportedException.class);
        when(ex.getMethod()).thenReturn("POST");

        ResponseEntity<ErroreResponse> risposta = handler.gestisciMetodoNonConsentito(ex, request("/api/misurazioni"));

        assertEquals(HttpStatus.METHOD_NOT_ALLOWED, risposta.getStatusCode());
        assertTrue(risposta.getBody().messaggio().contains("POST"));
    }
}