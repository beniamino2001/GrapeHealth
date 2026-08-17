package it.pegasopw.grapehealth.decisionengine.stato;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

public class StatoRischio {

    private record LetturaTemporale(Instant timestamp, double valore) {}

    public record MediaGiornaliera(double temperaturaMedia, double umiditaMedia) {}

    private record AccumuloGiorno(LocalDate giorno, double sommaTemperatura, double sommaUmidita, int conteggio) {
        AccumuloGiorno aggiungi(double temperatura, double umidita) {
            return new AccumuloGiorno(giorno, sommaTemperatura + temperatura, sommaUmidita + umidita, conteggio + 1);
        }

        MediaGiornaliera media() {
            return new MediaGiornaliera(sommaTemperatura / conteggio, sommaUmidita / conteggio);
        }
    }

    private final ConcurrentHashMap<String, String> ultimoLivelloRischio = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Double> ultimoValoreCorrente = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, ConcurrentLinkedDeque<LetturaTemporale>> accumuloTemporale = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Instant> inizioEpisodio = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, AccumuloGiorno> accumuloGiorno = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Double> percentualeIncubazione = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, LocalDate> ultimoGiornoPioggiaRegistrato = new ConcurrentHashMap<>();

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

    public void registraValoreCorrente(String chiave, double valore) {
        ultimoValoreCorrente.put(chiave, valore);
    }

    public Double valoreCorrente(String chiave) {
        return ultimoValoreCorrente.get(chiave);
    }

    public void registraLetturaTemporale(String chiave, Instant timestamp, double valore) {
        accumuloTemporale.computeIfAbsent(chiave, k -> new ConcurrentLinkedDeque<>())
                .add(new LetturaTemporale(timestamp, valore));
    }

    /**
     * Variante di registraLetturaTemporale() per grandezze che, come "pioggia" nel
     * payload del simulatore (sensors-simulator/simulator/generator.py, campo
     * pioggia_oggi_mm), arrivano come un TOTALE CUMULATO DI GIORNATA ripubblicato
     * invariato a ogni ciclo di pubblicazione (~2880 messaggi/giorno simulato con
     * intervallo di 30s), non come letture indipendenti da sommare fra loro.
     *
     * Registra al più un campione per giorno solare per chiave: la prima lettura di
     * un nuovo giorno viene accodata normalmente, le letture successive dello STESSO
     * giorno vengono scartate perché ridondanti (stesso totale di giornata, non un
     * incremento). Così sommaFinestra() somma il totale di ciascun giorno realmente
     * rientrante nella finestra, non lo stesso totale ripetuto centinaia di volte.
     *
     * Assunzione dichiarata: il valore resta costante per l'intera giornata simulata.
     * Se in futuro un sensore reale o un simulatore aggiornasse il totale progressivamente 
     * nel corso della stessa giornata, andrebbe sostituito il campione del giorno 
     * con l'ultimo valore noto invece di scartare le letture successive.
     */
    public void registraLetturaGiornalieraPioggia(String chiave, Instant timestamp, double valore) {
        LocalDate giorno = timestamp.atZone(ZoneOffset.UTC).toLocalDate();
        LocalDate giornoPrecedente = ultimoGiornoPioggiaRegistrato.put(chiave, giorno);
        if (!giorno.equals(giornoPrecedente)) {
            registraLetturaTemporale(chiave, timestamp, valore);
        }
    }

    public double sommaFinestra(String chiave, Instant ora, Duration finestra) {
        ConcurrentLinkedDeque<LetturaTemporale> letture = accumuloTemporale.get(chiave);
        if (letture == null) {
            return 0.0;
        }
        Instant limite = ora.minus(finestra);
        letture.removeIf(l -> l.timestamp().isBefore(limite));
        return letture.stream().mapToDouble(LetturaTemporale::valore).sum();
    }

    public void iniziaEpisodio(String chiave, Instant timestamp) {
        inizioEpisodio.putIfAbsent(chiave, timestamp);
    }

    public Instant inizioEpisodio(String chiave) {
        return inizioEpisodio.get(chiave);
    }

    public void terminaEpisodio(String chiave) {
        inizioEpisodio.remove(chiave);
    }

    /**
     * Accumula una lettura di temperatura/umidità nel giorno corrente (in
     * tempo simulato, ricavato dal timestamp della misurazione). Se la
     * lettura appartiene a un nuovo giorno rispetto all'ultimo accumulo per
     * questa chiave, restituisce la media del giorno appena concluso e apre
     * un nuovo accumulo; altrimenti accumula nel giorno corrente e restituisce
     * un Optional vuoto. Stesso principio di "un aggiornamento per giorno
     * simulato" già usato dal simulatore Python per la deriva di psi_stem.
     */
    public Optional<MediaGiornaliera> accumulaEChiudiGiornoSeNuovo(String chiave, Instant timestamp,
                                                                   double temperatura, double umidita) {
        LocalDate giorno = timestamp.atZone(ZoneOffset.UTC).toLocalDate();
        AccumuloGiorno precedente = accumuloGiorno.get(chiave);

        if (precedente == null) {
            accumuloGiorno.put(chiave, new AccumuloGiorno(giorno, temperatura, umidita, 1));
            return Optional.empty();
        }

        if (precedente.giorno().equals(giorno)) {
            accumuloGiorno.put(chiave, precedente.aggiungi(temperatura, umidita));
            return Optional.empty();
        }

        accumuloGiorno.put(chiave, new AccumuloGiorno(giorno, temperatura, umidita, 1));
        return Optional.of(precedente.media());
    }

    public double percentualeIncubazione(String chiave) {
        return percentualeIncubazione.getOrDefault(chiave, 0.0);
    }

    public void incrementaPercentualeIncubazione(String chiave, double incremento) {
        percentualeIncubazione.merge(chiave, incremento, Double::sum);
    }

    public void azzeraPercentualeIncubazione(String chiave) {
        percentualeIncubazione.remove(chiave);
        accumuloGiorno.remove(chiave);
    }
}