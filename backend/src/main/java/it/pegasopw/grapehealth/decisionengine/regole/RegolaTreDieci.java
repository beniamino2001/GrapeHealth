package it.pegasopw.grapehealth.decisionengine.regole;

import it.pegasopw.grapehealth.decisionengine.cache.CacheGermogli;
import it.pegasopw.grapehealth.decisionengine.cache.CacheTabellaGoidanich;
import it.pegasopw.grapehealth.decisionengine.model.dto.MisurazioneMessage;
import it.pegasopw.grapehealth.decisionengine.model.evento.AllertaEvent;
import it.pegasopw.grapehealth.decisionengine.stato.StatoRischio;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;

@Component
public class RegolaTreDieci implements RegolaRischio {

    private static final String TIPO = "tre_dieci";
    private static final String PARAMETRO_TEMPERATURA = "temperatura_aria";
    private static final String PARAMETRO_PIOGGIA = "pioggia";
    private static final String PARAMETRO_UMIDITA = "umidita_aria";

    private static final double SOGLIA_TEMPERATURA = 10.0;
    private static final double SOGLIA_PIOGGIA_MM = 10.0;
    private static final double SOGLIA_GERMOGLI_CM = 10.0;

    private static final Duration FINESTRA_PIOGGIA = Duration.ofHours(48);
    private static final double SOGLIA_TRATTAMENTO_PERCENTUALE = 70.0;

    private final CacheGermogli cacheGermogli;
    private final CacheTabellaGoidanich cacheTabellaGoidanich;

    public RegolaTreDieci(CacheGermogli cacheGermogli, CacheTabellaGoidanich cacheTabellaGoidanich) {
        this.cacheGermogli = cacheGermogli;
        this.cacheTabellaGoidanich = cacheTabellaGoidanich;
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

        String chiaveParcella = TIPO + ":" + m.parcella();
        String chiaveTemperatura = chiaveParcella + ":temperatura";
        String chiaveUmidita = chiaveParcella + ":umidita";
        String chiavePioggia = chiaveParcella + ":pioggia";

        switch (m.parametro()) {
            case PARAMETRO_TEMPERATURA -> stato.registraValoreCorrente(chiaveTemperatura, m.valore());
            case PARAMETRO_UMIDITA -> stato.registraValoreCorrente(chiaveUmidita, m.valore());
            default -> stato.registraLetturaGiornalieraPioggia(chiavePioggia, m.timestampRilevazione(), m.valore());
        }

        Double temperaturaCorrente = stato.valoreCorrente(chiaveTemperatura);
        Double umiditaCorrente = stato.valoreCorrente(chiaveUmidita);
        double pioggiaCumulata = stato.sommaFinestra(chiavePioggia, m.timestampRilevazione(), FINESTRA_PIOGGIA);
        double germogliCm = cacheGermogli.lunghezzaCm(m.parcella());

        boolean condizioneVerificata = temperaturaCorrente != null
                && temperaturaCorrente >= SOGLIA_TEMPERATURA
                && pioggiaCumulata >= SOGLIA_PIOGGIA_MM
                && germogliCm >= SOGLIA_GERMOGLI_CM;

        String livelloAttuale = stato.livelloRischio(chiaveParcella);

        if (condizioneVerificata && livelloAttuale == null) {
            stato.aggiornaLivelloRischio(chiaveParcella, "moderato");
            return Optional.of(new AllertaEvent(
                    TIPO, "moderato", m.nodo(), m.parcella(), m.parametro(), m.valore(),
                    messaggioBaldacci(m.parcella(), temperaturaCorrente, pioggiaCumulata, germogliCm),
                    m.timestampRilevazione()));
        }

        if ("moderato".equals(livelloAttuale) && temperaturaCorrente != null && umiditaCorrente != null) {
            return aggiornaIncubazione(stato, chiaveParcella, m, temperaturaCorrente, umiditaCorrente);
        }

        return Optional.empty();
    }

    private Optional<AllertaEvent> aggiornaIncubazione(StatoRischio stato, String chiaveParcella,
                                                       MisurazioneMessage m, double temperatura, double umidita) {

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
                .formatted(parcella, temperatura, pioggia, FINESTRA_PIOGGIA.toHours(), germogli);
    }
}