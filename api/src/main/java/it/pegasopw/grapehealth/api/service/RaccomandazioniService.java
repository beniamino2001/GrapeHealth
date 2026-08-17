package it.pegasopw.grapehealth.api.service;

import it.pegasopw.grapehealth.api.cache.CacheAzioniMitigazione;
import it.pegasopw.grapehealth.api.cache.CacheRegole;
import it.pegasopw.grapehealth.api.exception.RisorsaNonTrovataException;
import it.pegasopw.grapehealth.api.model.dto.AzioneMitigazioneDTO;
import it.pegasopw.grapehealth.api.model.dto.RaccomandazioneDTO;
import it.pegasopw.grapehealth.api.model.dto.SogliaDTO;
import it.pegasopw.grapehealth.api.model.entity.AllertaEntity;
import it.pegasopw.grapehealth.api.model.entity.RegolaAzioneEntity;
import it.pegasopw.grapehealth.api.model.entity.RegolaEntity;
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
    private final CacheRegole cacheRegole;
    private final CacheAzioniMitigazione cacheAzioniMitigazione;

    public RaccomandazioniService(AllertaRepository allertaRepository,
                                  TrattamentoRepository trattamentoRepository,
                                  MappatoreRaccomandazione mappatoreRaccomandazione,
                                  CacheRegole cacheRegole,
                                  CacheAzioniMitigazione cacheAzioniMitigazione) {
        this.allertaRepository = allertaRepository;
        this.trattamentoRepository = trattamentoRepository;
        this.mappatoreRaccomandazione = mappatoreRaccomandazione;
        this.cacheRegole = cacheRegole;
        this.cacheAzioniMitigazione = cacheAzioniMitigazione;
    }

    public RaccomandazioneDTO perAllerta(Long allertaId) {
        AllertaEntity allerta = allertaRepository.findById(allertaId)
                .orElseThrow(() -> new RisorsaNonTrovataException("Nessuna allerta trovata con id " + allertaId));

        Optional<TrattamentoEntity> trattamento = trattamentoRepository.findFirstByAllertaId(allertaId);
        return costruisci(allerta, trattamento);
    }

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

    public List<RaccomandazioneDTO> perAllerteMultiple(List<Long> allertaIds) {
        List<AllertaEntity> allerte = allertaRepository.findAllById(allertaIds);

        List<Long> idsTrovati = allerte.stream().map(AllertaEntity::getId).toList();
        Map<Long, TrattamentoEntity> trattamentiPerAllerta = trattamentoRepository.findByAllertaIdIn(idsTrovati)
                .stream()
                .collect(Collectors.toMap(TrattamentoEntity::getAllertaId, Function.identity(), (a, b) -> a));

        return allerte.stream()
                .map(allerta -> costruisci(allerta, Optional.ofNullable(trattamentiPerAllerta.get(allerta.getId()))))
                .toList();
    }

    private RaccomandazioneDTO costruisci(AllertaEntity allerta, Optional<TrattamentoEntity> trattamento) {
        String azioneConsigliata = mappatoreRaccomandazione.azioneConsigliata(allerta);
        String testo = mappatoreRaccomandazione.testoRaccomandazione(allerta);

        RegolaEntity regola = cacheRegole.trovaPerCodice(allerta.getRegolaCodice());
        List<AzioneMitigazioneDTO> azioniAlternative = cacheAzioniMitigazione.azioniPerRegola(allerta.getRegolaCodice())
                .stream()
                .map(this::toAzioneMitigazioneDTO)
                .toList();
        List<SogliaDTO> soglieRegola = cacheRegole.sogliePerRegola(allerta.getRegolaCodice())
                .stream()
                .map(s -> new SogliaDTO(s.getParametro(), s.getLivelloRischio(), s.getOperatore(),
                        s.getValoreSoglia(), s.getUnitaMisura(), s.getDurataMinimaMinuti(), s.getNote()))
                .toList();

        return trattamento.map(t -> new RaccomandazioneDTO(
                        allerta.getId(), allerta.getTipo(), allerta.getLivelloRischio(),
                        azioneConsigliata, testo, true,
                        t.getTipoAzione(), t.getEsito(), t.getEseguitoIl(),
                        regola != null ? regola.getDescrizione() : null,
                        regola != null ? regola.getFonteBibliografica() : null,
                        azioniAlternative, soglieRegola))
                .orElseGet(() -> new RaccomandazioneDTO(
                        allerta.getId(), allerta.getTipo(), allerta.getLivelloRischio(),
                        azioneConsigliata, testo, false, null, null, null,
                        regola != null ? regola.getDescrizione() : null,
                        regola != null ? regola.getFonteBibliografica() : null,
                        azioniAlternative, soglieRegola));
    }

    private AzioneMitigazioneDTO toAzioneMitigazioneDTO(RegolaAzioneEntity regolaAzione) {
        var azione = cacheAzioniMitigazione.trovaAzionePerCodice(regolaAzione.getAzioneCodice());
        return new AzioneMitigazioneDTO(
                regolaAzione.getAzioneCodice(),
                azione != null ? azione.getDescrizione() : null,
                azione != null ? azione.getFonteBibliografica() : null,
                regolaAzione.getNote()
        );
    }
}