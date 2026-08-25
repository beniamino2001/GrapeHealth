package it.pegasopw.grapehealth.api.raccomandazione;

import it.pegasopw.grapehealth.api.model.entity.AllertaEntity;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;

class MappatoreRaccomandazioneTest {

    private final MappatoreRaccomandazione mappatore = new MappatoreRaccomandazione();

    private AllertaEntity allerta(String tipo, String livelloRischio) {
        AllertaEntity entity = new AllertaEntity();
        ReflectionTestUtils.setField(entity, "tipo", tipo);
        ReflectionTestUtils.setField(entity, "livelloRischio", livelloRischio);
        return entity;
    }

    @Test
    void treDieciModeratoRaccomandaTrattamentoMirato() {
        String testo = mappatore.testoRaccomandazione(allerta("tre_dieci", "moderato"));
        assertTrue(testo.contains("mirato"));
        assertFalse(testo.contains("urgente"));
    }

    @Test
    void treDieciSeveroRaccomandaTrattamentoUrgente() {
        String testo = mappatore.testoRaccomandazione(allerta("tre_dieci", "severo"));
        assertTrue(testo.contains("urgente"));
        assertFalse(testo.contains("mirato"));
    }

    @Test
    void stressIdricoInterpolaIlLivelloNelTesto() {
        String testo = mappatore.testoRaccomandazione(allerta("stress_idrico", "severo"));
        assertTrue(testo.contains("severo"));
    }

    @Test
    void tipoNonGestitoLanciaEccezione() {
        assertThrows(IllegalArgumentException.class,
                () -> mappatore.testoRaccomandazione(allerta("tipo_sconosciuto", "moderato")));
    }

    @Test
    void svernamentoOosporeNonHaAzioneCatalogata() {
        assertTrue(mappatore.azioneConsigliata(allerta("svernamento_oospore", "moderato")).isEmpty());
    }

    @Test
    void infezioneSecondariaNonHaAzioneCatalogata() {
        assertTrue(mappatore.azioneConsigliata(allerta("infezione_secondaria", "moderato")).isEmpty());
    }

    @Test
    void treDieciHaAzioneCatalogata() {
        assertTrue(mappatore.azioneConsigliata(allerta("tre_dieci", "moderato")).isPresent());
        assertEquals("trattamento_fitosanitario", mappatore.azioneConsigliata(allerta("tre_dieci", "moderato")).get());
    }

    @Test
    void svernamentoOosporeETestoInformativoSenzaRaccomandazioneDiAzione() {
        String testo = mappatore.testoRaccomandazione(allerta("svernamento_oospore", "moderato"));
        assertTrue(testo.contains("monitoraggio"));
        assertFalse(testo.contains("si raccomanda"));
    }

    @Test
    void dannoRadicaleNonHaAzioneCatalogata() {
        assertTrue(mappatore.azioneConsigliata(allerta("danno_radicale", "severo")).isEmpty());
    }

    @Test
    void dannoRadicaleTestoInformativoSenzaRaccomandazioneDiAzione() {
        String testo = mappatore.testoRaccomandazione(allerta("danno_radicale", "severo"));
        assertTrue(testo.contains("monitoraggio"));
        assertFalse(testo.contains("si raccomanda"));
    }
}