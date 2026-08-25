package it.pegasopw.grapehealth.decisionengine.cache;

import it.pegasopw.grapehealth.decisionengine.model.entity.RegolaSogliaEntity;
import it.pegasopw.grapehealth.decisionengine.repository.RegolaSogliaRepository;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CacheSoglieRegoleTest {

    private RegolaSogliaEntity soglia(String regola, String parametro, String livello, String operatore, double valore) {
        RegolaSogliaEntity s = new RegolaSogliaEntity();
        s.setRegolaCodice(regola);
        s.setParametro(parametro);
        s.setLivelloRischio(livello);
        s.setOperatore(operatore);
        s.setValoreSoglia(valore);
        return s;
    }

    @Test
    void trovaLUnicaSogliaCorrispondente() {
        RegolaSogliaRepository repo = mock(RegolaSogliaRepository.class);
        when(repo.findAll()).thenReturn(List.of(soglia("stress_idrico", "psi_stem", "moderato", "<=", -1.2)));
        CacheSoglieRegole cache = new CacheSoglieRegole(repo);
        cache.carica();

        assertEquals(-1.2, cache.sogliaUnica("stress_idrico", "psi_stem", "moderato").getValoreSoglia());
    }

    @Test
    void lanciaEccezioneSeLaSogliaCercataNonEsiste() {
        RegolaSogliaRepository repo = mock(RegolaSogliaRepository.class);
        when(repo.findAll()).thenReturn(List.of());
        CacheSoglieRegole cache = new CacheSoglieRegole(repo);
        cache.carica();

        assertThrows(IllegalStateException.class,
                () -> cache.sogliaUnica("stress_idrico", "psi_stem", "moderato"));
    }

    @Test
    void lanciaEccezioneSeLaCombinazioneENonAmbiguaSenzaOperatore() {
        RegolaSogliaRepository repo = mock(RegolaSogliaRepository.class);
        when(repo.findAll()).thenReturn(List.of(
                soglia("svernamento_oospore", "temperatura_suolo", "moderato", ">=", 12.0),
                soglia("svernamento_oospore", "temperatura_suolo", "moderato", "<=", 32.0)));
        CacheSoglieRegole cache = new CacheSoglieRegole(repo);
        cache.carica();

        assertThrows(IllegalStateException.class,
                () -> cache.sogliaUnica("svernamento_oospore", "temperatura_suolo", "moderato"));
    }

    @Test
    void disambiguaConLOperatoreQuandoDueRigheCondividonoLoStessoLivello() {
        RegolaSogliaRepository repo = mock(RegolaSogliaRepository.class);
        when(repo.findAll()).thenReturn(List.of(
                soglia("svernamento_oospore", "temperatura_suolo", "moderato", ">=", 12.0),
                soglia("svernamento_oospore", "temperatura_suolo", "moderato", "<=", 32.0)));
        CacheSoglieRegole cache = new CacheSoglieRegole(repo);
        cache.carica();

        assertEquals(12.0, cache.sogliaUnica("svernamento_oospore", "temperatura_suolo", "moderato", ">=").getValoreSoglia());
        assertEquals(32.0, cache.sogliaUnica("svernamento_oospore", "temperatura_suolo", "moderato", "<=").getValoreSoglia());
    }

    @Test
    void soglieMultipleRestituisceTutteLeRigheCorrispondenti() {
        RegolaSogliaRepository repo = mock(RegolaSogliaRepository.class);
        when(repo.findAll()).thenReturn(List.of(
                soglia("sunburn", "temperatura_bacca", "severo", ">=", 53.79),
                soglia("sunburn", "temperatura_bacca", "severo", ">=", 49.94)));
        CacheSoglieRegole cache = new CacheSoglieRegole(repo);
        cache.carica();

        assertEquals(2, cache.soglieMultiple("sunburn", "temperatura_bacca", "severo").size());
    }

    @Test
    void soglieDiRestituisceListaVuotaPerUnCodiceNonSeminato() {
        RegolaSogliaRepository repo = mock(RegolaSogliaRepository.class);
        when(repo.findAll()).thenReturn(List.of());
        CacheSoglieRegole cache = new CacheSoglieRegole(repo);
        cache.carica();

        assertTrue(cache.soglieDi("regola_inesistente").isEmpty());
    }
}