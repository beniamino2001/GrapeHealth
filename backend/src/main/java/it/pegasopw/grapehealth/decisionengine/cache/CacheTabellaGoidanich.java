package it.pegasopw.grapehealth.decisionengine.cache;

import it.pegasopw.grapehealth.decisionengine.model.entity.SogliaIncubazioneGoidanichEntity;
import it.pegasopw.grapehealth.decisionengine.repository.SogliaIncubazioneGoidanichRepository;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.NavigableMap;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Cache in memoria della tabella di incubazione di Goidanich (peronospora),
 * caricata una sola volta all'avvio dalla tabella soglia_incubazione_goidanich.
 *
 * Due assunzioni dichiarate restano nel codice, non nel database, perché
 * riguardano COME interrogare la tabella, non i dati della tabella stessa:
 *
 * 1) Temperature sotto i 14°C (fuori dal range 14-26 vincolato dal CHECK
 *    della tabella) usano per estrapolazione la riga più bassa disponibile.
 * 2) La soglia che separa "umidità bassa" da "umidità alta" (≥90% di umidità
 *    relativa) è convenzionale, non specificamente attribuibile a Goidanich.
 */
@Component
public class CacheTabellaGoidanich {

    private static final Logger log = LoggerFactory.getLogger(CacheTabellaGoidanich.class);
    private static final double SOGLIA_UMIDITA_ALTA = 90.0;

    private record Riga(double percentualeUmiditaBassa, double percentualeUmiditaAlta) {}

    private final SogliaIncubazioneGoidanichRepository repository;
    private final NavigableMap<Integer, Riga> tabella = new TreeMap<>();

    public CacheTabellaGoidanich(SogliaIncubazioneGoidanichRepository repository) {
        this.repository = repository;
    }

    @PostConstruct
    public void carica() {
        ConcurrentHashMap<Integer, double[]> grezzo = new ConcurrentHashMap<>();
        for (SogliaIncubazioneGoidanichEntity riga : repository.findAll()) {
            int temperatura = riga.getId().getTemperaturaMedia();
            double[] coppia = grezzo.computeIfAbsent(temperatura, t -> new double[2]);
            if (Boolean.TRUE.equals(riga.getId().getUmiditaAlta())) {
                coppia[1] = riga.getPercentualeIncrementoGiornaliero();
            } else {
                coppia[0] = riga.getPercentualeIncrementoGiornaliero();
            }
        }
        grezzo.forEach((temperatura, coppia) -> tabella.put(temperatura, new Riga(coppia[0], coppia[1])));
        log.info("Cache tabella di Goidanich caricata: {} temperature note", tabella.size());
    }

    public double percentualeGiornaliera(double temperaturaMedia, double umiditaMedia) {
        Integer chiave = tabella.floorKey((int) Math.floor(temperaturaMedia));
        if (chiave == null) {
            chiave = tabella.firstKey();
        }
        Riga riga = tabella.get(chiave);
        return umiditaMedia >= SOGLIA_UMIDITA_ALTA ? riga.percentualeUmiditaAlta() : riga.percentualeUmiditaBassa();
    }
}