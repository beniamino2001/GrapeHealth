package it.pegasopw.grapehealth.decisionengine.regole;

import it.pegasopw.grapehealth.decisionengine.cache.CacheGermogli;
import it.pegasopw.grapehealth.decisionengine.model.dto.MisurazioneMessage;
import it.pegasopw.grapehealth.decisionengine.model.entity.ParcellaEntity;
import it.pegasopw.grapehealth.decisionengine.repository.ParcellaRepository;
import it.pegasopw.grapehealth.decisionengine.stato.StatoRischio;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RegolaTreDieciTest {

    private RegolaTreDieci regola;
    private final StatoRischio stato = new StatoRischio();
    private final Instant ora = Instant.parse("2026-04-15T10:00:00Z"); // data in cui c'è un germogliamento primaverile plausibile

    private ParcellaEntity parcella(String nome, double lunghezzaCm) {
        ParcellaEntity entity = new ParcellaEntity();
        entity.setNome(nome);
        entity.setLunghezzaGermoglioCm(lunghezzaCm);
        return entity;
    }

    @BeforeEach
    void creaRegolaConCacheGermogliDiTest() {
        // stessi valori del seed di infra/postgres/init/01_schema.sql e coerenti con sensors-simulator/config/nodi.yaml
        ParcellaRepository repository = mock(ParcellaRepository.class);
        when(repository.findAll()).thenReturn(List.of(
                parcella("parcellaA", 12.0),
                parcella("parcellaB", 14.0),
                parcella("parcellaC", 10.0)));

        CacheGermogli cacheGermogli = new CacheGermogli(repository);
        cacheGermogli.carica();

        regola = new RegolaTreDieci(cacheGermogli);
    }

    private MisurazioneMessage temperatura(String parcella, double valore, Instant t) {
        return new MisurazioneMessage("meteo-X1", parcella, "temperatura_aria", valore, "C", t);
    }

    private MisurazioneMessage pioggia(String parcella, double valore, Instant t) {
        return new MisurazioneMessage("meteo-X1", parcella, "pioggia", valore, "mm", t);
    }

    @Test
    void scattaQuandoTutteETreLeCondizioniSonoVerificate() {
        // parcellaC ha germogli a 10cm esattamente (soglia inclusiva, "≥10")
        regola.valuta(temperatura("parcellaC", 12.0, ora), stato);
        var risultato = regola.valuta(pioggia("parcellaC", 11.0, ora), stato);

        assertTrue(risultato.isPresent());
        assertEquals("tre_dieci", risultato.get().tipo());
    }

    @Test
    void nonScattaSeLaTemperaturaENonAncoraNota() {
        var risultato = regola.valuta(pioggia("parcellaA", 15.0, ora), stato);
        assertTrue(risultato.isEmpty());
    }

    @Test
    void nonScattaSeLaPioggiaCumulataENonAncoraSufficiente() {
        regola.valuta(temperatura("parcellaA", 14.0, ora), stato);
        var risultato = regola.valuta(pioggia("parcellaA", 4.0, ora), stato);
        assertTrue(risultato.isEmpty());
    }

    @Test
    void sommaCorrettamentePioggeMultipleNellaFinestra() {
        regola.valuta(temperatura("parcellaA", 14.0, ora), stato);
        regola.valuta(pioggia("parcellaA", 4.0, ora), stato);
        regola.valuta(pioggia("parcellaA", 3.0, ora.plus(6, ChronoUnit.HOURS)), stato);
        var risultato = regola.valuta(pioggia("parcellaA", 4.0, ora.plus(12, ChronoUnit.HOURS)), stato);

        assertTrue(risultato.isPresent());
    }

    @Test
    void escludePioggiaFuoriDallaFinestraDi48Ore() {
        regola.valuta(temperatura("parcellaA", 14.0, ora), stato);
        regola.valuta(pioggia("parcellaA", 8.0, ora), stato);

        var risultato = regola.valuta(
                pioggia("parcellaA", 3.0, ora.plus(50, ChronoUnit.HOURS)), stato);

        assertTrue(risultato.isEmpty());
    }

    @Test
    void nonScattaSeLoStadioFenologicoENonSufficiente() {
        // parcella non presente nella cache di test -> lunghezzaCm() restituisce 0.0
        regola.valuta(temperatura("parcellaSconosciuta", 14.0, ora), stato);
        var risultato = regola.valuta(pioggia("parcellaSconosciuta", 15.0, ora), stato);
        assertTrue(risultato.isEmpty());
    }

    @Test
    void nonRipubblicaSeLaCondizioneRestaVerificata() {
        regola.valuta(temperatura("parcellaB", 15.0, ora), stato);
        regola.valuta(pioggia("parcellaB", 11.0, ora), stato);

        var secondoGiro = regola.valuta(pioggia("parcellaB", 1.0, ora.plus(1, ChronoUnit.HOURS)), stato);
        assertTrue(secondoGiro.isEmpty());
    }

    @Test
    void ignoraParametriNonPertinenti() {
        var m = new MisurazioneMessage("idrico-A1", "parcellaA", "psi_stem", -1.5, "MPa", ora);
        assertTrue(regola.valuta(m, stato).isEmpty());
    }
}