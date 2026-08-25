package it.pegasopw.grapehealth.decisionengine.regole;

import it.pegasopw.grapehealth.decisionengine.cache.CacheSoglieRegole;
import it.pegasopw.grapehealth.decisionengine.model.dto.MisurazioneMessage;
import it.pegasopw.grapehealth.decisionengine.model.evento.AllertaEvent;
import it.pegasopw.grapehealth.decisionengine.stato.StatoRischio;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * Rischio di infezione secondaria di peronospora per bagnatura fogliare
 * prolungata a temperatura favorevole (Brischetto et al., 2021): condizione
 * di rischio quando bagnatura_fogliare resta almeno alla soglia per almeno
 * la durata minima continuativa, con temperatura_aria contemporaneamente
 * compresa nella banda — tutti e tre i valori letti a runtime da
 * regola_soglia. Fenomeno distinto dal trigger di Baldacci di
 * RegolaTreDieci: qui l'infezione riguarda spore già presenti sulla
 * vegetazione, non la prima infezione primaria della stagione.
 *
 * La soglia su bagnatura_fogliare è una convenzione di questo progetto, non
 * un valore di Brischetto et al.: il modello originale definisce "ora umida"
 * come umidità relativa ≥80% oppure pioggia >0mm oppure bagnatura fogliare
 * >30min (un OR fra tre segnali), qui semplificato al solo segnale di
 * bagnatura fogliare. L'isteresi di 5 punti percentuali attorno a quella
 * soglia (l'episodio si interrompe solo sotto soglia-5, non a ogni lettura
 * sotto soglia) assorbe il rumore di misura di ±5% dichiarato dal generatore
 * del simulatore. La stessa logica si applica indipendentemente alla banda
 * di temperatura (isteresi di 1°C): con la ricalibrazione di temperatura_aria
 * in ondata di calore, il confine superiore di questa banda viene
 * attraversato regolarmente. Nessuna delle due isteresi ha una colonna
 * dedicata in regola_soglia, restano costanti Java.
 */
@Component
public class RegolaInfezioneSecondaria implements RegolaRischio {

    private static final String TIPO = "infezione_secondaria";
    private static final String PARAMETRO_BAGNATURA = "bagnatura_fogliare";
    private static final String PARAMETRO_TEMPERATURA = "temperatura_aria";

    private static final double ISTERESI_BAGNATURA = 5.0;
    private static final double ISTERESI_TEMPERATURA = 1.0;

    private final double sogliaBagnatura;
    private final Duration durataMinima;
    private final double temperaturaMinima;
    private final double temperaturaMassima;

    public RegolaInfezioneSecondaria(CacheSoglieRegole cacheSoglieRegole) {
        var sogliaBagnaturaEntity = cacheSoglieRegole.sogliaUnica(TIPO, PARAMETRO_BAGNATURA, "moderato");
        this.sogliaBagnatura = sogliaBagnaturaEntity.getValoreSoglia();
        this.durataMinima = Duration.ofMinutes(sogliaBagnaturaEntity.getDurataMinimaMinuti());
        this.temperaturaMinima = cacheSoglieRegole.sogliaUnica(TIPO, PARAMETRO_TEMPERATURA, "moderato", ">=").getValoreSoglia();
        this.temperaturaMassima = cacheSoglieRegole.sogliaUnica(TIPO, PARAMETRO_TEMPERATURA, "moderato", "<=").getValoreSoglia();
    }

    @Override
    public boolean isApplicabile(MisurazioneMessage m) {
        return PARAMETRO_BAGNATURA.equals(m.parametro()) || PARAMETRO_TEMPERATURA.equals(m.parametro());
    }

    @Override
    public Optional<AllertaEvent> valuta(MisurazioneMessage m, StatoRischio stato) {
        if (!isApplicabile(m)) {
            return Optional.empty();
        }

        String chiaveParcella = TIPO + ":" + m.parcella();
        String chiaveEpisodio = chiaveParcella + ":episodio";
        String chiaveTemperatura = chiaveParcella + ":temperatura";

        if (PARAMETRO_TEMPERATURA.equals(m.parametro())) {
            stato.registraValoreCorrente(chiaveTemperatura, m.valore());
        } else {
            aggiornaEpisodioBagnatura(stato, chiaveEpisodio, m.valore(), m.timestampRilevazione());
        }

        Instant inizioEpisodio = stato.inizioEpisodio(chiaveEpisodio);
        Double temperaturaCorrente = stato.valoreCorrente(chiaveTemperatura);
        String livelloPrecedente = stato.livelloRischio(chiaveParcella);
        boolean livelloGiaAttivo = livelloPrecedente != null;

        boolean durataSufficiente = inizioEpisodio != null
                && !Duration.between(inizioEpisodio, m.timestampRilevazione()).minus(durataMinima).isNegative();
        boolean temperaturaNellaBanda = temperaturaCorrente != null
                && temperaturaCorrente >= temperaturaMinima && temperaturaCorrente <= temperaturaMassima;
        boolean temperaturaNellaBandaConIsteresi = temperaturaCorrente != null
                && temperaturaCorrente >= temperaturaMinima - ISTERESI_TEMPERATURA
                && temperaturaCorrente <= temperaturaMassima + ISTERESI_TEMPERATURA;
        boolean temperaturaFavorevole = temperaturaNellaBanda || (livelloGiaAttivo && temperaturaNellaBandaConIsteresi);
        boolean condizioneVerificata = durataSufficiente && temperaturaFavorevole;

        if (condizioneVerificata && livelloPrecedente == null) {
            stato.aggiornaLivelloRischio(chiaveParcella, "moderato");
            return Optional.of(new AllertaEvent(
                    TIPO, "moderato", m.nodo(), m.parcella(), m.parametro(), m.valore(),
                    ("Bagnatura fogliare su %s sostenuta da almeno %d minuti con temperatura dell'aria a %.1f°C, " +
                            "nella banda favorevole all'infezione secondaria (%.1f-%.1f°C)")
                            .formatted(m.parcella(), durataMinima.toMinutes(), temperaturaCorrente,
                                    temperaturaMinima, temperaturaMassima),
                    m.timestampRilevazione()));
        }

        if (!condizioneVerificata && livelloPrecedente != null) {
            stato.aggiornaLivelloRischio(chiaveParcella, null);
        }

        return Optional.empty();
    }

    private void aggiornaEpisodioBagnatura(StatoRischio stato, String chiaveEpisodio, double valore, Instant ora) {
        boolean episodioGiaAttivo = stato.inizioEpisodio(chiaveEpisodio) != null;
        boolean bagnaturaSufficiente = valore >= sogliaBagnatura
                || (episodioGiaAttivo && valore >= sogliaBagnatura - ISTERESI_BAGNATURA);

        if (bagnaturaSufficiente) {
            stato.iniziaEpisodio(chiaveEpisodio, ora);
        } else {
            stato.terminaEpisodio(chiaveEpisodio);
        }
    }
}