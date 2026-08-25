package it.pegasopw.grapehealth.attuatori.simulazione;

import it.pegasopw.grapehealth.attuatori.model.evento.AllertaEvent;
import org.junit.jupiter.api.Test;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class SimulatoreAttuazioneTest {

    private final SimulatoreAttuazione simulatore = new SimulatoreAttuazione();

    private AllertaEvent evento(String tipo) {
        return eventoConLivello(tipo, "moderato");
    }

    private AllertaEvent eventoConLivello(String tipo, String livello) {
        return new AllertaEvent(tipo, livello, "nodo-test", "parcellaA",
                "parametro-test", 0.0, "messaggio-test", Instant.now());
    }

    @Test
    void stressIdricoAttivaIrrigazione() {
        assertEquals("irrigazione di soccorso", simulatore.determinaAzione(evento("stress_idrico")));
    }

    @Test
    void ondataDiCaloreModeratoAttivaNebulizzazioneSemplice() {
        assertEquals("nebulizzazione anti-calore", simulatore.determinaAzione(evento("ondata_di_calore")));
    }

    @Test
    void ondataDiCaloreSeveroAttivaNebulizzazioneDEmergenza() {
        assertEquals("nebulizzazione anti-calore d'emergenza",
                simulatore.determinaAzione(eventoConLivello("ondata_di_calore", "severo")));
    }

    @Test
    void sunburnAttivaNebulizzazioneAntiScottatura() {
        assertEquals("nebulizzazione anti-scottatura", simulatore.determinaAzione(evento("sunburn")));
    }

    @Test
    void treDieciModeratoAttivaTrattamentoFitosanitarioMirato() {
        assertEquals("trattamento fitosanitario mirato", simulatore.determinaAzione(evento("tre_dieci")));
    }

    @Test
    void treDieciSeveroAttivaTrattamentoFitosanitarioUrgente() {
        assertEquals("trattamento fitosanitario urgente",
                simulatore.determinaAzione(eventoConLivello("tre_dieci", "severo")));
    }

    @Test
    void svernamentoOosporeNonHaAzioneCatalogata() {
        assertEquals("nessuna azione di mitigazione catalogata (monitoraggio)",
                simulatore.determinaAzione(evento("svernamento_oospore")));
    }

    @Test
    void infezioneSecondariaNonHaAzioneCatalogata() {
        assertEquals("nessuna azione di mitigazione catalogata (monitoraggio)",
                simulatore.determinaAzione(evento("infezione_secondaria")));
    }

    @Test
    void dannoRadicaleNonHaAzioneCatalogata() {
        assertEquals("nessuna azione di mitigazione catalogata (monitoraggio)",
                simulatore.determinaAzione(eventoConLivello("danno_radicale", "severo")));
    }

    @Test
    void tipoSconosciutoLanciaEccezione() {
        assertThrows(IllegalArgumentException.class, () -> simulatore.determinaAzione(evento("tipo_inesistente")));
    }

    @Test
    void stressIdricoModeratoAttivaIrrigazioneSemplice() {
        assertEquals("irrigazione di soccorso",
                simulatore.determinaAzione(eventoConLivello("stress_idrico", "moderato")));
    }

    @Test
    void stressIdricoSeveroAttivaIrrigazioneDEmergenza() {
        assertEquals("irrigazione di soccorso d'emergenza",
                simulatore.determinaAzione(eventoConLivello("stress_idrico", "severo")));
    }

    @Test
    void sunburnModeratoAttivaNebulizzazioneSemplice() {
        assertEquals("nebulizzazione anti-scottatura",
                simulatore.determinaAzione(eventoConLivello("sunburn", "moderato")));
    }

    @Test
    void sunburnSeveroAttivaNebulizzazioneDEmergenza() {
        assertEquals("nebulizzazione anti-scottatura d'emergenza",
                simulatore.determinaAzione(eventoConLivello("sunburn", "severo")));
    }
}