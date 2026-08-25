package it.pegasopw.grapehealth.decisionengine.regole;

import it.pegasopw.grapehealth.decisionengine.cache.CacheSoglieRegole;
import it.pegasopw.grapehealth.decisionengine.model.dto.MisurazioneMessage;
import it.pegasopw.grapehealth.decisionengine.model.entity.RegolaSogliaEntity;
import it.pegasopw.grapehealth.decisionengine.stato.StatoRischio;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CoesistenzaRegoleTest {

    private final RegolaStressIdrico regolaIdrico = new RegolaStressIdrico(cacheStressIdrico());
    private final RegolaOndataDiCalore regolaCalore = new RegolaOndataDiCalore(cacheOndataDiCalore());
    private final StatoRischio statoCondiviso = new StatoRischio();

    private RegolaSogliaEntity soglia(double valore) {
        RegolaSogliaEntity s = new RegolaSogliaEntity();
        s.setValoreSoglia(valore);
        return s;
    }

    private CacheSoglieRegole cacheStressIdrico() {
        CacheSoglieRegole cache = mock(CacheSoglieRegole.class);
        when(cache.sogliaUnica("stress_idrico", "psi_stem", "moderato")).thenReturn(soglia(-1.2));
        when(cache.sogliaUnica("stress_idrico", "psi_stem", "severo")).thenReturn(soglia(-1.4));
        return cache;
    }

    private CacheSoglieRegole cacheOndataDiCalore() {
        CacheSoglieRegole cache = mock(CacheSoglieRegole.class);
        when(cache.sogliaUnica("ondata_di_calore", "temperatura_aria", "moderato")).thenReturn(soglia(35.0));
        when(cache.sogliaUnica("ondata_di_calore", "temperatura_aria", "severo")).thenReturn(soglia(40.0));
        return cache;
    }

    @Test
    void ogniRegolaIgnoraLeMisurazioniDiCompetenzaAltrui() {
        var misurazioneTemperatura = new MisurazioneMessage("X1", "parcellaTest", "temperatura_aria", 40.0, "C", Instant.now());
        var misurazionePsiStem = new MisurazioneMessage("X1", "parcellaTest", "psi_stem", -1.5, "MPa", Instant.now());

        // Qui la regola idrica non deve reagire a una misurazione di temperatura.
        assertTrue(regolaIdrico.valuta(misurazioneTemperatura, statoCondiviso).isEmpty());
        // Qui la regola calore non deve reagire a una misurazione di psi_stem.
        assertTrue(regolaCalore.valuta(misurazionePsiStem, statoCondiviso).isEmpty());
    }

    @Test
    void loStatoDiUnaRegolaNonInterferisceConLAltraSulloStessoNodo() {
        String nodo = "X1";

        var allertaCalore = regolaCalore.valuta(
                new MisurazioneMessage(nodo, "parcellaTest", "temperatura_aria", 40.0, "C", Instant.now()),
                statoCondiviso);
        assertTrue(allertaCalore.isPresent());

        var allertaIdrico = regolaIdrico.valuta(
                new MisurazioneMessage(nodo, "parcellaTest", "psi_stem", -1.25, "MPa", Instant.now()),
                statoCondiviso);
        assertTrue(allertaIdrico.isPresent());
        assertEquals("stress_idrico", allertaIdrico.get().tipo());
        assertEquals("moderato", allertaIdrico.get().livelloRischio());
    }

    @Test
    void laDeduplicaRestaIndipendenteTraLeDueRegoleSulloStessoNodo() {
        String nodo = "X1";

        // Qui la regola calore scatta una volta e non deve ripubblicare al secondo giro.
        regolaCalore.valuta(new MisurazioneMessage(nodo, "parcellaTest", "temperatura_aria", 36.0, "C", Instant.now()), statoCondiviso);
        var secondoGiroCalore = regolaCalore.valuta(new MisurazioneMessage(nodo, "parcellaTest", "temperatura_aria", 36.2, "C", Instant.now()), statoCondiviso);
        assertTrue(secondoGiroCalore.isEmpty());

        // Qui non si deve impedire alla regola idrica di scattare per la prima volta sullo stesso nodo.
        var primoGiroIdrico = regolaIdrico.valuta(new MisurazioneMessage(nodo, "parcellaTest", "psi_stem", -1.25, "MPa", Instant.now()), statoCondiviso);
        assertTrue(primoGiroIdrico.isPresent());
    }

    @Test
    void nessunaDelleDueRegoleGeneraAllerteDaiParametriDiSuoloVentoERadiazione() {
        String parcella = "parcellaTest";
        var nuoviParametri = new MisurazioneMessage[] {
                new MisurazioneMessage("meteo-X1", parcella, "velocita_vento", 2.5, "m/s", Instant.now()),
                new MisurazioneMessage("meteo-X1", parcella, "radiazione_solare", 500.0, "W/m2", Instant.now()),
                new MisurazioneMessage("suolo-X1", parcella, "temperatura_suolo", 22.0, "C", Instant.now()),
                // umidita_suolo è tracciata da RegolaStressIdrico (v. RegolaStressIdricoTest)
                // ma non genera mai un'allerta da sola
                new MisurazioneMessage("suolo-X1", parcella, "umidita_suolo", 18.0, "%", Instant.now())
        };

        for (MisurazioneMessage m : nuoviParametri) {
            assertTrue(regolaIdrico.valuta(m, statoCondiviso).isEmpty());
            assertTrue(regolaCalore.valuta(m, statoCondiviso).isEmpty());
        }
    }
}