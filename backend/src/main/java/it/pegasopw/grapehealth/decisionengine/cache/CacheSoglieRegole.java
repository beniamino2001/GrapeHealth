package it.pegasopw.grapehealth.decisionengine.cache;

import it.pegasopw.grapehealth.decisionengine.model.entity.RegolaSogliaEntity;
import it.pegasopw.grapehealth.decisionengine.repository.RegolaSogliaRepository;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Cache in memoria di tutte le soglie di regola_soglia, raggruppate per
 * regola_codice, caricata una sola volta all'avvio. Ogni classe Regola*
 * riceve questa cache nel proprio costruttore e ne estrae, sempre
 * all'avvio, esattamente i valori di cui ha bisogno per i propri campi
 * finali — non la interroga a ogni misurazione: la lettura da database
 * resta un costo di avvio, non di percorso critico, sullo stesso principio
 * già seguito da CacheGermogli e CacheTabellaGoidanich.
 *
 * Le isteresi (0,05 MPa su stress_idrico, 1°C sulle altre) non hanno una
 * colonna dedicata in regola_soglia — restano annotate solo nel campo
 * "note" in formato libero, non parsabile in modo affidabile — quindi
 * restano costanti Java in ciascuna regola, non lette da qui.
 */
@Component
public class CacheSoglieRegole {

    private static final Logger log = LoggerFactory.getLogger(CacheSoglieRegole.class);

    private final RegolaSogliaRepository regolaSogliaRepository;
    private final Map<String, List<RegolaSogliaEntity>> soglieDiRegola = new ConcurrentHashMap<>();

    public CacheSoglieRegole(RegolaSogliaRepository regolaSogliaRepository) {
        this.regolaSogliaRepository = regolaSogliaRepository;
    }

    @PostConstruct
    void carica() {
        for (RegolaSogliaEntity soglia : regolaSogliaRepository.findAll()) {
            soglieDiRegola.computeIfAbsent(soglia.getRegolaCodice(), k -> new ArrayList<>()).add(soglia);
        }
        int totale = soglieDiRegola.values().stream().mapToInt(List::size).sum();
        log.info("Cache soglie regole caricata: {} soglie su {} regole", totale, soglieDiRegola.size());
    }

    /** Tutte le soglie della regola indicata; lista vuota se il codice non è seminato. */
    public List<RegolaSogliaEntity> soglieDi(String regolaCodice) {
        return soglieDiRegola.getOrDefault(regolaCodice, List.of());
    }

    /**
     * Tutte le soglie della regola per parametro e livello indicati, quando
     * più righe condividono la stessa combinazione — es. le quattro coppie
     * soglia/durata di sunburn, distinte solo da durata_minima_minuti.
     * A differenza di sogliaUnica(), qui più di una riga è attesa, non un
     * errore.
     */
    public List<RegolaSogliaEntity> soglieMultiple(String regolaCodice, String parametro, String livelloRischio) {
        return soglieDi(regolaCodice).stream()
                .filter(s -> parametro.equals(s.getParametro()) && livelloRischio.equals(s.getLivelloRischio()))
                .toList();
    }

    /**
     * L'unica soglia della regola per parametro e livello indicati. Lancia
     * IllegalStateException se manca o se è ambigua (più righe corrispondenti,
     * es. una banda con due righe sullo stesso livello distinte solo
     * dall'operatore — in quel caso usare la variante a quattro argomenti).
     * Un fallimento a runtime, non un valore silenziosamente sbagliato: se
     * schema e codice divergono, meglio saperlo all'avvio.
     */
    public RegolaSogliaEntity sogliaUnica(String regolaCodice, String parametro, String livelloRischio) {
        return unica(soglieMultiple(regolaCodice, parametro, livelloRischio),
                regolaCodice, parametro, livelloRischio, null);
    }

    /** Come sogliaUnica(), con l'operatore come ulteriore chiave di disambiguazione. */
    public RegolaSogliaEntity sogliaUnica(String regolaCodice, String parametro, String livelloRischio, String operatore) {
        List<RegolaSogliaEntity> candidate = soglieDi(regolaCodice).stream()
                .filter(s -> parametro.equals(s.getParametro()) && livelloRischio.equals(s.getLivelloRischio())
                        && operatore.equals(s.getOperatore()))
                .toList();
        return unica(candidate, regolaCodice, parametro, livelloRischio, operatore);
    }

    private RegolaSogliaEntity unica(List<RegolaSogliaEntity> candidate, String regolaCodice, String parametro,
                                     String livelloRischio, String operatore) {
        if (candidate.size() != 1) {
            throw new IllegalStateException(
                    "Attesa esattamente una soglia per regola=%s, parametro=%s, livello=%s, operatore=%s, trovate %d"
                            .formatted(regolaCodice, parametro, livelloRischio, operatore, candidate.size()));
        }
        return candidate.get(0);
    }
}