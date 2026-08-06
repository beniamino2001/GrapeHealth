package it.pegasopw.grapehealth.persistence.simulazione;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;

class StimaScalaSimulazioneTest {

    private static final double DELTA_CONSENTITO = 0.01;

    @Test
    void scalaDiDefaultENeutraFinoAllaPrimaCoppiaUtile() {
        StimaScalaSimulazione stima = new StimaScalaSimulazione();
        assertEquals(1.0, stima.scalaCorrente(), DELTA_CONSENTITO);

        // Una sola osservazione non basta a calcolare un delta: la scala resta invariata.
        stima.osserva(Instant.parse("2026-08-04T10:00:00Z"), Instant.parse("2026-08-04T10:00:00Z"));
        assertEquals(1.0, stima.scalaCorrente(), DELTA_CONSENTITO);
    }

    @Test
    void inferisceUnaScalaAltaDaDueOsservazioniRavvicinateNelTempoReale() {
        StimaScalaSimulazione stima = new StimaScalaSimulazione();

        Instant realeT0 = Instant.parse("2026-08-04T10:00:00Z");
        Instant simulatoT0 = Instant.parse("2026-08-04T10:00:00Z");
        stima.osserva(simulatoT0, realeT0);

        // 120 secondi simulati passati in 1 secondo reale => scala istantanea 120
        Instant realeT1 = realeT0.plusSeconds(1);
        Instant simulatoT1 = simulatoT0.plusSeconds(120);
        stima.osserva(simulatoT1, realeT1);

        // media mobile: 0.2*120 + 0.8*1.0 = 24.8
        assertEquals(24.8, stima.scalaCorrente(), DELTA_CONSENTITO);
    }

    @Test
    void convergeVersoLaScalaRealeConOsservazioniRipetute() {
        StimaScalaSimulazione stima = new StimaScalaSimulazione();

        Instant reale = Instant.parse("2026-08-04T10:00:00Z");
        Instant simulato = Instant.parse("2026-08-04T10:00:00Z");
        stima.osserva(simulato, reale);

        for (int i = 0; i < 50; i++) {
            reale = reale.plusSeconds(1);
            simulato = simulato.plusSeconds(120);
            stima.osserva(simulato, reale);
        }

        // dopo molte osservazioni coerenti, la media mobile converge vicino a 120
        assertEquals(120.0, stima.scalaCorrente(), 1.0);
    }

    @Test
    void ignoraUnaCoppiaConDeltaRealeNonPositivo() {
        StimaScalaSimulazione stima = new StimaScalaSimulazione();

        Instant reale = Instant.parse("2026-08-04T10:00:00Z");
        Instant simulato = Instant.parse("2026-08-04T10:00:00Z");
        stima.osserva(simulato, reale);

        // stesso istante reale (messaggi arrivati nello stesso millisecondo,
        // arrotondati): nessun delta reale utile, la stima non deve cambiare
        stima.osserva(simulato.plusSeconds(10), reale);

        assertEquals(1.0, stima.scalaCorrente(), DELTA_CONSENTITO);
    }
}