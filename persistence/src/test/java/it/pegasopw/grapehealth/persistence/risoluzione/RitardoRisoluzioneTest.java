package it.pegasopw.grapehealth.persistence.risoluzione;

import it.pegasopw.grapehealth.persistence.simulazione.StimaScalaSimulazione;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RitardoRisoluzioneTest {

    // In uno scenario "scala 1:1", i ritardi devono corrispondere per intero alle durate realistiche dichiarate in ritardoBase(...)
    private final RitardoRisoluzione ritardo = new RitardoRisoluzione(new StimaScalaSimulazione());

    @Test
    void aScalaUnitariaRestituisceLeDurateRealisticheComplete() {
        assertEquals(Duration.ofHours(2), ritardo.perAllerta("stress_idrico", "moderato"));
        assertEquals(Duration.ofHours(4), ritardo.perAllerta("stress_idrico", "severo"));
        assertEquals(Duration.ofMinutes(20), ritardo.perAllerta("sunburn", "moderato"));
        assertEquals(Duration.ofMinutes(40), ritardo.perAllerta("sunburn", "severo"));
        assertEquals(Duration.ofMinutes(20), ritardo.perAllerta("ondata_di_calore", "moderato"));
        assertEquals(Duration.ofMinutes(40), ritardo.perAllerta("ondata_di_calore", "severo"));
        assertEquals(Duration.ofHours(24), ritardo.perAllerta("tre_dieci", "moderato"));
        assertEquals(Duration.ofHours(6), ritardo.perAllerta("tre_dieci", "severo"));
        assertEquals(Duration.ofHours(24), ritardo.perAllerta("svernamento_oospore", "moderato"));
        assertEquals(Duration.ofHours(24), ritardo.perAllerta("infezione_secondaria", "moderato"));
        assertEquals(Duration.ofHours(24), ritardo.perAllerta("danno_radicale", "severo"));
    }

    @Test
    void stressIdricoSeveroImpiegaPiuTempoDiModerato() {
        Duration moderato = ritardo.perAllerta("stress_idrico", "moderato");
        Duration severo = ritardo.perAllerta("stress_idrico", "severo");
        assertTrue(severo.compareTo(moderato) > 0);
    }

    @Test
    void sunburnSeveroImpiegaPiuTempoDiModerato() {
        Duration moderato = ritardo.perAllerta("sunburn", "moderato");
        Duration severo = ritardo.perAllerta("sunburn", "severo");
        assertTrue(severo.compareTo(moderato) > 0);
    }

    @Test
    void ondataDiCaloreSeveroImpiegaPiuTempoDiModerato() {
        Duration moderato = ritardo.perAllerta("ondata_di_calore", "moderato");
        Duration severo = ritardo.perAllerta("ondata_di_calore", "severo");
        assertTrue(severo.compareTo(moderato) > 0);
        assertEquals(ritardo.perAllerta("sunburn", "severo"), severo,
                "stessa azione (nebulizzazione) di sunburn: stessa durata");
    }

    @Test
    void treDieciSeveroImpiegaMenoTempoDiModeratoPercheLaFinestraSiStaChiudendo() {
        Duration moderato = ritardo.perAllerta("tre_dieci", "moderato");
        Duration severo = ritardo.perAllerta("tre_dieci", "severo");
        assertTrue(severo.compareTo(moderato) < 0,
                "tre_dieci è l'unico tipo dove severo è più urgente, quindi più breve, non più lungo");
    }

    @Test
    void treDieciModeratoHaIlRitardoPiuLungoTraITipiConAzione() {
        Duration treDieciModerato = ritardo.perAllerta("tre_dieci", "moderato");
        assertTrue(treDieciModerato.compareTo(ritardo.perAllerta("stress_idrico", "severo")) > 0);
        assertTrue(treDieciModerato.compareTo(ritardo.perAllerta("sunburn", "severo")) > 0);
        assertTrue(treDieciModerato.compareTo(ritardo.perAllerta("ondata_di_calore", "severo")) > 0);
    }

    @Test
    void iTreTipiSenzaAzioneCatalogataHannoLoStessoOrdineDiGrandezzaDiTreDieciModerato() {
        Duration treDieciModerato = ritardo.perAllerta("tre_dieci", "moderato");
        assertEquals(treDieciModerato, ritardo.perAllerta("svernamento_oospore", "moderato"));
        assertEquals(treDieciModerato, ritardo.perAllerta("infezione_secondaria", "moderato"));
        assertEquals(treDieciModerato, ritardo.perAllerta("danno_radicale", "severo"));
    }

    @Test
    void tipoSconosciutoLanciaEccezione() {
        assertThrows(IllegalArgumentException.class, () -> ritardo.perAllerta("tipo_inesistente", "moderato"));
    }
}