package it.pegasopw.grapehealth.persistence.simulazione;

import it.pegasopw.grapehealth.persistence.risoluzione.RitardoRisoluzione;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RitardoRisoluzioneConScalaTest {

    @Test
    void aScala1IlRitardoERealisticoEIntero() {
        StimaScalaSimulazione stima = new StimaScalaSimulazione();
        RitardoRisoluzione ritardo = new RitardoRisoluzione(stima);

        assertEquals(Duration.ofHours(2), ritardo.perAllerta("stress_idrico", "moderato"));
        assertEquals(Duration.ofHours(4), ritardo.perAllerta("stress_idrico", "severo"));
        assertEquals(Duration.ofMinutes(20), ritardo.perAllerta("sunburn", "moderato"));
        assertEquals(Duration.ofHours(24), ritardo.perAllerta("tre_dieci", "moderato"));
    }

    @Test
    void aScalaAltaIlRitardoSiComprimeProporzionalmente() {
        StimaScalaSimulazione stima = new StimaScalaSimulazione();

        // Forza la stima a convergere verso una scala nota (~2880, la stessa
        // usata tipicamente con il simulatore Python) con osservazioni
        // sintetiche ripetute
        Instant reale = Instant.parse("2026-08-04T10:00:00Z");
        Instant simulato = Instant.parse("2026-08-04T10:00:00Z");
        stima.osserva(simulato, reale);
        for (int i = 0; i < 100; i++) {
            reale = reale.plusSeconds(1);
            simulato = simulato.plusSeconds(2880);
            stima.osserva(simulato, reale);
        }
        assertEquals(2880.0, stima.scalaCorrente(), 5.0);

        RitardoRisoluzione ritardo = new RitardoRisoluzione(stima);

        // stress_idrico moderato: 2h / ~2880 = ~2,5s -> sotto il minimo, floor a 10s
        // (qui il pavimento assorbe qualunque piccolo scarto sulla scala stimata,
        // quindi l'uguaglianza esatta e' legittima)
        assertEquals(Duration.ofSeconds(10), ritardo.perAllerta("stress_idrico", "moderato"));

        // tre_dieci: 24h / ~2880 = ~30s -> sopra il minimo, la differenziazione resta
        // visibile. Qui la scala stimata converge solo asintoticamente a 2880.0
        // (mai in modo esatto in un numero finito di osservazioni), quindi si
        // confronta con una tolleranza invece che con un'uguaglianza esatta
        Duration treDieci = ritardo.perAllerta("tre_dieci", "moderato");
        double secondiOttenuti = treDieci.toNanos() / 1_000_000_000.0;
        assertEquals(30.0, secondiOttenuti, 0.1);
        assertTrue(treDieci.compareTo(ritardo.perAllerta("sunburn", "moderato")) > 0);
    }
}