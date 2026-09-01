package it.pegasopw.grapehealth.decisionengine.stato;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class StatoRischioTest {

    private final StatoRischio stato = new StatoRischio();

    @Test
    void livelloRischioNulloRimuoveLaChiaveInveceDiMemorizzareNull() {
        stato.aggiornaLivelloRischio("chiave", "moderato");
        assertEquals("moderato", stato.livelloRischio("chiave"));

        stato.aggiornaLivelloRischio("chiave", null);
        // non "null" come stringa: la chiave deve sparire, altrimenti un
        // successivo livelloRischio("chiave") != null darebbe un falso positivo
        assertNull(stato.livelloRischio("chiave"));
    }

    @Test
    void sommaFinestraScartaLeLettureFuoriDallaFinestraTemporale() {
        Instant t0 = Instant.parse("2026-01-01T00:00:00Z");
        stato.registraLetturaTemporale("pioggia", t0, 5.0);
        stato.registraLetturaTemporale("pioggia", t0.plus(Duration.ofHours(50)), 3.0);

        // Qui la prima lettura è fuori dalla finestra delle 48h rispetto a "ora": non deve contribuire alla somma.
        double somma = stato.sommaFinestra("pioggia", t0.plus(Duration.ofHours(50)), Duration.ofHours(48));
        assertEquals(3.0, somma, 0.001);
    }

    @Test
    void iniziaEpisodioNonSpostaMaiLInizioDiUnEpisodioGiaAttivo() {
        Instant inizio = Instant.parse("2026-01-01T10:00:00Z");
        stato.iniziaEpisodio("episodio", inizio);
        stato.iniziaEpisodio("episodio", inizio.plus(Duration.ofMinutes(30)));

        // Qui l'inizio registrato deve restare il primo, non l'ultimo: la durata
        // di un episodio si misura dal suo vero inizio, non dall'ultima conferma.
        assertEquals(inizio, stato.inizioEpisodio("episodio"));
    }

    @Test
    void terminaEpisodioRimuoveDavveroLoStatoNonSoloLoAzzeraFormalmente() {
        stato.iniziaEpisodio("episodio", Instant.now());
        stato.terminaEpisodio("episodio");
        assertNull(stato.inizioEpisodio("episodio"));
    }

    @Test
    void accumulaEChiudiGiornoRestituisceVuotoFinoAlCambioDiGiorno() {
        Instant mattina = Instant.parse("2026-01-01T06:00:00Z");
        Instant sera = Instant.parse("2026-01-01T20:00:00Z");

        assertTrue(stato.accumulaEChiudiGiornoSeNuovo("chiave", mattina, 20.0, 60.0).isEmpty());
        assertTrue(stato.accumulaEChiudiGiornoSeNuovo("chiave", sera, 30.0, 40.0).isEmpty());

        Instant giornoDopo = Instant.parse("2026-01-02T06:00:00Z");
        var media = stato.accumulaEChiudiGiornoSeNuovo("chiave", giornoDopo, 22.0, 55.0);

        assertTrue(media.isPresent());
        // Qui la media deve essere quella delle due letture del giorno 1 (20+30)/2 e
        // (60+40)/2, non contaminata dalla lettura che ha aperto il giorno 2.
        assertEquals(25.0, media.get().temperaturaMedia(), 0.001);
        assertEquals(50.0, media.get().umiditaMedia(), 0.001);
    }

    @Test
    void accumulaEChiudiGiornoRestaCorrettoAncheConUnSaltoDiPiuGiorniSimulati() {
        // Scenario tipico di un time_scale del simulatore molto elevato (oltre 9000,
        // v. StatoRischio§registraLetturaGiornalieraPioggia): fra due letture
        // consecutive per la stessa chiave passano più di 24 ore simulate, saltando
        // interi giorni solari senza nessuna lettura.
        Instant giorno1 = Instant.parse("2026-01-01T10:00:00Z");
        Instant giorno5 = Instant.parse("2026-01-05T10:00:00Z"); // giorni 2, 3 e 4 mai osservati

        assertTrue(stato.accumulaEChiudiGiornoSeNuovo("chiave", giorno1, 20.0, 60.0).isEmpty());
        var media = stato.accumulaEChiudiGiornoSeNuovo("chiave", giorno5, 25.0, 70.0);

        // Qui il salto non deve produrre un'eccezione né una media corrotta: la
        // media restituita è quella dell'unico giorno realmente osservato (il primo),
        // non una media spuria calcolata su un intervallo di più giorni.
        assertTrue(media.isPresent());
        assertEquals(20.0, media.get().temperaturaMedia(), 0.001);
        assertEquals(60.0, media.get().umiditaMedia(), 0.001);

        // Il nuovo accumulo riparte pulito dal giorno 5, non trascina nulla del salto.
        var chiusuraSuccessiva = stato.accumulaEChiudiGiornoSeNuovo(
                "chiave", giorno5.plus(Duration.ofDays(1)), 18.0, 65.0);
        assertTrue(chiusuraSuccessiva.isPresent());
        assertEquals(25.0, chiusuraSuccessiva.get().temperaturaMedia(), 0.001);
    }

    @Test
    void registraLetturaGiornalieraPioggiaScartaLeRipubblicazioniDelloStessoGiorno() {
        Instant t0 = Instant.parse("2026-01-01T10:00:00Z");
        stato.registraLetturaGiornalieraPioggia("pioggia", t0, 12.0);
        // stesso giorno solare, valore ripubblicato invariato come fa davvero il simulatore
        stato.registraLetturaGiornalieraPioggia("pioggia", t0.plus(Duration.ofMinutes(15)), 12.0);
        stato.registraLetturaGiornalieraPioggia("pioggia", t0.plus(Duration.ofMinutes(30)), 12.0);

        // Qui, se le ripubblicazioni non venissero scartate, la somma sarebbe 36 non 12.
        double somma = stato.sommaFinestra("pioggia", t0.plus(Duration.ofMinutes(30)), Duration.ofHours(48));
        assertEquals(12.0, somma, 0.001);
    }

    @Test
    void registraLetturaGiornalieraPioggiaRestaCorrettaAncheConUnSaltoDiPiuGiorniSimulati() {
        Instant giorno1 = Instant.parse("2026-01-01T10:00:00Z");
        Instant giorno5 = Instant.parse("2026-01-05T10:00:00Z");

        stato.registraLetturaGiornalieraPioggia("pioggia", giorno1, 8.0);
        stato.registraLetturaGiornalieraPioggia("pioggia", giorno5, 6.0);

        // Qui il salto non deve far crollare né duplicare la somma: i due giorni
        // realmente osservati contano una volta ciascuno (8+6=14), i tre giorni
        // saltati in mezzo semplicemente non contribuiscono - non c'è modo di
        // recuperare un dato mai arrivato, ma non deve nemmeno essere inventato.
        double somma = stato.sommaFinestra("pioggia", giorno5, Duration.ofDays(10));
        assertEquals(14.0, somma, 0.001);
    }

    @Test
    void azzeraPercentualeIncubazioneRipulisceAncheLAccumuloDelGiornoInCorso() {
        Instant t0 = Instant.parse("2026-01-01T10:00:00Z");
        stato.incrementaPercentualeIncubazione("parcella", 30.0);
        stato.accumulaEChiudiGiornoSeNuovo("parcella", t0, 20.0, 60.0);

        stato.azzeraPercentualeIncubazione("parcella");

        assertEquals(0.0, stato.percentualeIncubazione("parcella"), 0.001);
        // Qui l'accumulo del giorno in corso deve ripartire da zero: un nuovo ciclo
        // Baldacci->Goidanich non deve ereditare temperature/umidità di un ciclo
        // di incubazione già concluso e azzerato.
        var primaLetturaNuovoCiclo = stato.accumulaEChiudiGiornoSeNuovo("parcella", t0.plus(Duration.ofDays(1)), 25.0, 70.0);
        assertTrue(primaLetturaNuovoCiclo.isEmpty());
    }
}