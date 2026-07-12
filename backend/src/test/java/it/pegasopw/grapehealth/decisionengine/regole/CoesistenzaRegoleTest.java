package it.pegasopw.grapehealth.decisionengine.regole;

import it.pegasopw.grapehealth.decisionengine.model.dto.MisurazioneMessage;
import it.pegasopw.grapehealth.decisionengine.stato.StatoRischio;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class CoesistenzaRegoleTest {

    private final RegolaStressIdrico regolaIdrico = new RegolaStressIdrico();
    private final RegolaOndataDiCalore regolaCalore = new RegolaOndataDiCalore();
    private final StatoRischio statoCondiviso = new StatoRischio(); // come nel listener: un'unica istanza

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
}