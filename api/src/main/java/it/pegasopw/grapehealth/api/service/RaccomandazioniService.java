package it.pegasopw.grapehealth.api.service;

import it.pegasopw.grapehealth.api.exception.RisorsaNonTrovataException;
import it.pegasopw.grapehealth.api.model.dto.RaccomandazioneDTO;
import it.pegasopw.grapehealth.api.model.entity.AllertaEntity;
import it.pegasopw.grapehealth.api.model.entity.TrattamentoEntity;
import it.pegasopw.grapehealth.api.raccomandazione.MappatoreRaccomandazione;
import it.pegasopw.grapehealth.api.repository.AllertaRepository;
import it.pegasopw.grapehealth.api.repository.TrattamentoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class RaccomandazioniService {

    private final AllertaRepository allertaRepository;
    private final TrattamentoRepository trattamentoRepository;
    private final MappatoreRaccomandazione mappatoreRaccomandazione;

    public RaccomandazioniService(AllertaRepository allertaRepository,
                                  TrattamentoRepository trattamentoRepository,
                                  MappatoreRaccomandazione mappatoreRaccomandazione) {
        this.allertaRepository = allertaRepository;
        this.trattamentoRepository = trattamentoRepository;
        this.mappatoreRaccomandazione = mappatoreRaccomandazione;
    }

    public RaccomandazioneDTO perAllerta(Long allertaId) {
        AllertaEntity allerta = allertaRepository.findById(allertaId)
                .orElseThrow(() -> new RisorsaNonTrovataException("Nessuna allerta trovata con id " + allertaId));

        Optional<TrattamentoEntity> trattamento = trattamentoRepository.findFirstByAllertaId(allertaId);
        return costruisci(allerta, trattamento);
    }

    /**
     * Raccomandazioni per tutte le allerte attive, arricchite in blocco (una sola query su trattamento per l'intero elenco, non una per allerta).
     */
    public List<RaccomandazioneDTO> perAllerteAttive() {
        List<AllertaEntity> allerteAttive = allertaRepository.findAll((root, query, cb) ->
                cb.equal(root.get("stato"), "attiva"));

        List<Long> allertaIds = allerteAttive.stream().map(AllertaEntity::getId).toList();
        Map<Long, TrattamentoEntity> trattamentiPerAllerta = trattamentoRepository.findByAllertaIdIn(allertaIds)
                .stream()
                .collect(Collectors.toMap(TrattamentoEntity::getAllertaId, Function.identity(), (a, b) -> a));

        return allerteAttive.stream()
                .map(allerta -> costruisci(allerta, Optional.ofNullable(trattamentiPerAllerta.get(allerta.getId()))))
                .toList();
    }

    private RaccomandazioneDTO costruisci(AllertaEntity allerta, Optional<TrattamentoEntity> trattamento) {
        String azioneConsigliata = mappatoreRaccomandazione.azioneConsigliata(allerta);
        String testo = mappatoreRaccomandazione.testoRaccomandazione(allerta);

        return trattamento.map(t -> new RaccomandazioneDTO(
                        allerta.getId(), allerta.getTipo(), allerta.getLivelloRischio(),
                        azioneConsigliata, testo, true,
                        t.getTipoAzione(), t.getEsito(), t.getEseguitoIl()))
                .orElseGet(() -> new RaccomandazioneDTO(
                        allerta.getId(), allerta.getTipo(), allerta.getLivelloRischio(),
                        azioneConsigliata, testo, false, null, null, null));
    }
}