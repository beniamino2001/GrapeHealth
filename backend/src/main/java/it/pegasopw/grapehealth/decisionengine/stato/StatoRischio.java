package it.pegasopw.grapehealth.decisionengine.stato;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

public class StatoRischio {

    private record LetturaTemporale(Instant timestamp, double valore) {}

    private final ConcurrentHashMap<String, String> ultimoLivelloRischio = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Double> ultimoValoreCorrente = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, ConcurrentLinkedDeque<LetturaTemporale>> accumuloTemporale = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Instant> inizioEpisodio = new ConcurrentHashMap<>();

    public String livelloRischio(String chiave) {
        return ultimoLivelloRischio.get(chiave);
    }

    public void aggiornaLivelloRischio(String chiave, String livello) {
        if (livello == null) {
            ultimoLivelloRischio.remove(chiave);
        } else {
            ultimoLivelloRischio.put(chiave, livello);
        }
    }

    // Memorizza l'ultimo valore noto per la chiave (es. l'ultima temperatura letta per una parcella).
    public void registraValoreCorrente(String chiave, double valore) {
        ultimoValoreCorrente.put(chiave, valore);
    }

    // Restituisce l'ultimo valore noto, o NULL se non è ancora arrivata alcuna lettura.
    public Double valoreCorrente(String chiave) {
        return ultimoValoreCorrente.get(chiave);
    }

    // Registra una lettura con timestamp, da usare per accumuli su finestra mobile (es. pioggia).
    public void registraLetturaTemporale(String chiave, Instant timestamp, double valore) {
        accumuloTemporale.computeIfAbsent(chiave, k -> new ConcurrentLinkedDeque<>())
                .add(new LetturaTemporale(timestamp, valore));
    }

    //Somma le letture registrate per la chiave data che ricadono nella finestra [ora - finestra, ora] ed elimina contestualmente le letture più vecchie della finestra, per evitare che la struttura cresca indefinitamente.
    public double sommaFinestra(String chiave, Instant ora, Duration finestra) {
        ConcurrentLinkedDeque<LetturaTemporale> letture = accumuloTemporale.get(chiave);
        if (letture == null) {
            return 0.0;
        }
        Instant limite = ora.minus(finestra);
        letture.removeIf(l -> l.timestamp().isBefore(limite));
        return letture.stream().mapToDouble(LetturaTemporale::valore).sum();
    }

    // Segna l'inizio di un episodio di esposizione continuativa, solo se non già aperto.
    public void iniziaEpisodio(String chiave, Instant timestamp) {
        inizioEpisodio.putIfAbsent(chiave, timestamp);
    }

    // Restituisce l'istante di inizio dell'episodio corrente, o NULL se non aperto.
    public Instant inizioEpisodio(String chiave) {
        return inizioEpisodio.get(chiave);
    }

    // Chiude l'episodio corrente (rientro genuino sotto soglia).
    public void terminaEpisodio(String chiave) {
        inizioEpisodio.remove(chiave);
    }
}