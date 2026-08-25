package it.pegasopw.grapehealth.decisionengine.regole;

import it.pegasopw.grapehealth.decisionengine.cache.CacheGermogli;
import it.pegasopw.grapehealth.decisionengine.cache.CacheSoglieRegole;
import it.pegasopw.grapehealth.decisionengine.cache.CacheTabellaGoidanich;
import it.pegasopw.grapehealth.decisionengine.model.dto.MisurazioneMessage;
import it.pegasopw.grapehealth.decisionengine.model.evento.AllertaEvent;
import it.pegasopw.grapehealth.decisionengine.stato.StatoRischio;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;

/**
 * Rischio di infezione primaria da peronospora (Plasmopara viticola), unica
 * regola del modulo a combinare due modelli bibliografici in cascata:
 *
 * 1) Trigger di Baldacci ("regola dei tre dieci") — livello "moderato".
 *    Condizione di infezione primaria verificata quando temperatura, pioggia
 *    cumulata e lunghezza del germoglio superano contemporaneamente 10 (in
 *    gradi, millimetri e centimetri rispettivamente), soglie e durata della
 *    finestra pioggia lette a runtime da regola_soglia.
 *
 * 2) Incubazione di Goidanich — livello "severo". Una volta scattato il
 *    trigger di Baldacci, la percentuale di sviluppo dell'incubazione viene
 *    stimata giorno per giorno da CacheTabellaGoidanich, in funzione della
 *    temperatura e dell'umidità medie del giorno appena concluso, finché non
 *    raggiunge la soglia di trattamento (70%, bibliografia: intervallo 70-80%,
 *    estremo più cautelativo) — questo valore non ha una riga in regola_soglia,
 *    appartiene al modello di Goidanich (CacheTabellaGoidanich), non a una
 *    condizione di soglia della regola dei tre dieci: resta costante Java. Le
 *    due fasi sono deliberatamente disaccoppiate: l'incubazione, una volta
 *    innescata, procede anche se la pioggia che l'ha originata è nel
 *    frattempo uscita dalla finestra — la domanda "è appena avvenuta
 *    un'infezione?" e la domanda "quanto è avanzata l'incubazione di
 *    un'infezione già avvenuta?" sono indipendenti.
 */
@Component
public class RegolaTreDieci implements RegolaRischio {

    private static final String TIPO = "tre_dieci";
    private static final String PARAMETRO_TEMPERATURA = "temperatura_aria";
    private static final String PARAMETRO_PIOGGIA = "pioggia";
    private static final String PARAMETRO_UMIDITA = "umidita_aria";
    private static final String PARAMETRO_GERMOGLI = "germogli";

    private static final double SOGLIA_TRATTAMENTO_PERCENTUALE = 70.0;

    private final CacheGermogli cacheGermogli;
    private final CacheTabellaGoidanich cacheTabellaGoidanich;
    private final double sogliaTemperatura;
    private final double sogliaPioggiaMm;
    private final double sogliaGermogliCm;
    private final Duration finestraPioggia;

    public RegolaTreDieci(CacheGermogli cacheGermogli, CacheTabellaGoidanich cacheTabellaGoidanich,
                          CacheSoglieRegole cacheSoglieRegole) {
        this.cacheGermogli = cacheGermogli;
        this.cacheTabellaGoidanich = cacheTabellaGoidanich;
        this.sogliaTemperatura = cacheSoglieRegole.sogliaUnica(TIPO, PARAMETRO_TEMPERATURA, "moderato").getValoreSoglia();
        var sogliaPioggia = cacheSoglieRegole.sogliaUnica(TIPO, PARAMETRO_PIOGGIA, "moderato");
        this.sogliaPioggiaMm = sogliaPioggia.getValoreSoglia();
        this.finestraPioggia = Duration.ofMinutes(sogliaPioggia.getDurataMinimaMinuti());
        this.sogliaGermogliCm = cacheSoglieRegole.sogliaUnica(TIPO, PARAMETRO_GERMOGLI, "moderato").getValoreSoglia();
    }

    @Override
    public boolean isApplicabile(MisurazioneMessage m) {
        return PARAMETRO_TEMPERATURA.equals(m.parametro())
                || PARAMETRO_PIOGGIA.equals(m.parametro())
                || PARAMETRO_UMIDITA.equals(m.parametro());
    }

    @Override
    public Optional<AllertaEvent> valuta(MisurazioneMessage m, StatoRischio stato) {
        if (!isApplicabile(m)) {
            return Optional.empty();
        }

        // Chiave a livello di parcella, non di nodo come nelle altre regole:
        // la condizione combina temperatura e pioggia sull'intera parcella,
        // anche se nel simulatore attuale condividono lo stesso nodo meteo.
        String chiaveParcella = TIPO + ":" + m.parcella();
        String chiaveTemperatura = chiaveParcella + ":temperatura";
        String chiaveUmidita = chiaveParcella + ":umidita";
        String chiavePioggia = chiaveParcella + ":pioggia";

        switch (m.parametro()) {
            case PARAMETRO_TEMPERATURA -> stato.registraValoreCorrente(chiaveTemperatura, m.valore());
            case PARAMETRO_UMIDITA -> stato.registraValoreCorrente(chiaveUmidita, m.valore());
            // "pioggia" arriva come totale cumulato di giornata, ripubblicato
            // invariato a ogni ciclo: registraLetturaGiornalieraPioggia scarta
            // le ripubblicazioni ridondanti (v. StatoRischio) invece di sommarle.
            default -> stato.registraLetturaGiornalieraPioggia(chiavePioggia, m.timestampRilevazione(), m.valore());
        }

        Double temperaturaCorrente = stato.valoreCorrente(chiaveTemperatura);
        Double umiditaCorrente = stato.valoreCorrente(chiaveUmidita);
        double pioggiaCumulata = stato.sommaFinestra(chiavePioggia, m.timestampRilevazione(), finestraPioggia);
        double germogliCm = cacheGermogli.lunghezzaCm(m.parcella());

        boolean condizioneVerificata = temperaturaCorrente != null
                && temperaturaCorrente >= sogliaTemperatura
                && pioggiaCumulata >= sogliaPioggiaMm
                && germogliCm >= sogliaGermogliCm;

        String livelloAttuale = stato.livelloRischio(chiaveParcella);

        // Trigger di Baldacci: la condizione scatta una sola volta, non si
        // ripubblica finché resta vera (livelloAttuale già "moderato" o "severo").
        if (condizioneVerificata && livelloAttuale == null) {
            stato.aggiornaLivelloRischio(chiaveParcella, "moderato");
            return Optional.of(new AllertaEvent(
                    TIPO, "moderato", m.nodo(), m.parcella(), m.parametro(), m.valore(),
                    messaggioBaldacci(m.parcella(), temperaturaCorrente, pioggiaCumulata, germogliCm),
                    m.timestampRilevazione()));
        }

        // Incubazione di Goidanich: prosegue finché il livello resta
        // "moderato", indipendentemente da condizioneVerificata (v. Javadoc
        // di classe sul disaccoppiamento fra le due fasi).
        if ("moderato".equals(livelloAttuale) && temperaturaCorrente != null && umiditaCorrente != null) {
            return aggiornaIncubazione(stato, chiaveParcella, m, temperaturaCorrente, umiditaCorrente);
        }

        return Optional.empty();
    }

    private Optional<AllertaEvent> aggiornaIncubazione(StatoRischio stato, String chiaveParcella,
                                                       MisurazioneMessage m, double temperatura, double umidita) {

        // Restituisce un valore solo quando il giorno simulato cambia: la
        // media di temperatura/umidita accumulata nel giorno appena concluso
        // determina l'incremento di incubazione di quel giorno.
        var mediaGiornoChiuso = stato.accumulaEChiudiGiornoSeNuovo(
                chiaveParcella, m.timestampRilevazione(), temperatura, umidita);
        if (mediaGiornoChiuso.isEmpty()) {
            return Optional.empty();
        }

        double percentualeGiorno = cacheTabellaGoidanich.percentualeGiornaliera(
                mediaGiornoChiuso.get().temperaturaMedia(), mediaGiornoChiuso.get().umiditaMedia());
        stato.incrementaPercentualeIncubazione(chiaveParcella, percentualeGiorno);
        double percentualeTotale = stato.percentualeIncubazione(chiaveParcella);

        if (percentualeTotale < SOGLIA_TRATTAMENTO_PERCENTUALE) {
            return Optional.empty();
        }

        // Soglia di trattamento raggiunta: il ciclo si chiude qui, azzerando
        // sia il livello di rischio sia l'incubazione accumulata, pronto per
        // un nuovo ciclo Baldacci->Goidanich alla prossima infezione primaria.
        stato.aggiornaLivelloRischio(chiaveParcella, null);
        stato.azzeraPercentualeIncubazione(chiaveParcella);

        String messaggio = ("Incubazione della peronospora su %s stimata al %.0f%%: " +
                "soglia di trattamento raggiunta, intervento raccomandato")
                .formatted(m.parcella(), percentualeTotale);

        return Optional.of(new AllertaEvent(
                TIPO, "severo", m.nodo(), m.parcella(), m.parametro(), m.valore(), messaggio, m.timestampRilevazione()));
    }

    private String messaggioBaldacci(String parcella, double temperatura, double pioggia, double germogli) {
        return ("Condizioni dei \"tre dieci\" verificate su %s: temperatura %.1f°C, " +
                "pioggia cumulata %.1f mm nelle ultime %d ore, germogli a %.0f cm — " +
                "rischio di infezione primaria da peronospora")
                .formatted(parcella, temperatura, pioggia, finestraPioggia.toHours(), germogli);
    }
}