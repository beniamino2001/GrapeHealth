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
        assertEquals(Duration.ofHours(24), ritardo.perAllerta("tre_dieci", "moderato"));
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
    void treDieciHaIlRitardoPiuLungoDiTutti() {
        Duration treDieci = ritardo.perAllerta("tre_dieci", "moderato");
        assertTrue(treDieci.compareTo(ritardo.perAllerta("stress_idrico", "severo")) > 0);
        assertTrue(treDieci.compareTo(ritardo.perAllerta("sunburn", "severo")) > 0);
        assertTrue(treDieci.compareTo(ritardo.perAllerta("ondata_di_calore", "moderato")) > 0);
    }

    @Test
    void tipoSconosciutoLanciaEccezione() {
        assertThrows(IllegalArgumentException.class, () -> ritardo.perAllerta("tipo_inesistente", "moderato"));
    }
}