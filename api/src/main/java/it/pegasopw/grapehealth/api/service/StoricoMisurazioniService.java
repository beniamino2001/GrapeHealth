package it.pegasopw.grapehealth.api.service;

import it.pegasopw.grapehealth.api.cache.CacheNodi;
import it.pegasopw.grapehealth.api.cache.CacheParcelle;
import it.pegasopw.grapehealth.api.exception.ParametriNonValidiException;
import it.pegasopw.grapehealth.api.mapper.MisurazioneMapper;
import it.pegasopw.grapehealth.api.model.dto.MisurazioneDTO;
import it.pegasopw.grapehealth.api.model.entity.MisurazioneEntity;
import it.pegasopw.grapehealth.api.repository.MisurazioneRepository;
import it.pegasopw.grapehealth.api.repository.spec.MisurazioneSpecifications;
import it.pegasopw.grapehealth.api.service.support.NodoResolver;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class StoricoMisurazioniService {

    private final MisurazioneRepository misurazioneRepository;
    private final NodoResolver nodoResolver;
    private final CacheNodi cacheNodi;
    private final CacheParcelle cacheParcelle;
    private static final int MAX_RIGHE_FINESTRA_TEMPORALE = 20_000;

    public StoricoMisurazioniService(MisurazioneRepository misurazioneRepository, NodoResolver nodoResolver,
                                     CacheNodi cacheNodi, CacheParcelle cacheParcelle) {
        this.misurazioneRepository = misurazioneRepository;
        this.nodoResolver = nodoResolver;
        this.cacheNodi = cacheNodi;
        this.cacheParcelle = cacheParcelle;
    }

    public Page<MisurazioneDTO> cerca(String parcella, String parametro, Instant dal, Instant al, Pageable pageable) {
        if (dal != null && al != null && dal.isAfter(al)) {
            throw new ParametriNonValidiException("Il parametro 'dal' non puo' essere successivo al parametro 'al'.");
        }

        List<Long> nodoIds = nodoResolver.nodoIdsPerParcella(parcella);
        if (nodoIds != null && nodoIds.isEmpty()) {
            return Page.empty(pageable);
        }

        Specification<MisurazioneEntity> filtro = MisurazioneSpecifications.nodoIdIn(nodoIds)
                .and(MisurazioneSpecifications.parametro(parametro))
                .and(MisurazioneSpecifications.rilevatoIlDopo(dal))
                .and(MisurazioneSpecifications.rilevatoIlPrima(al));

        // Una finestra temporale delimitata (dal e al entrambi presenti) è per definizione una
        // richiesta di "tutti i dati di questo intervallo", non una sfoglia pagina per pagina: una
        // singola pagina a dimensione fissa puo' troncare silenziosamente l'intervallo quando le
        // righe candidate superano il tetto di paginazione, restituendo solo le piu' recenti senza
        // segnalarlo. Per questo caso si interroga senza Pageable, ordinando cronologicamente: la
        // finestra è completa per costruzione, non solo nella pratica.
        if (dal != null && al != null) {
            long conteggio = misurazioneRepository.count(filtro);
            if (conteggio > MAX_RIGHE_FINESTRA_TEMPORALE) {
                throw new ParametriNonValidiException(
                        "L'intervallo richiesto restituirebbe %d righe, oltre il limite di %d: restringi 'dal'/'al'."
                                .formatted(conteggio, MAX_RIGHE_FINESTRA_TEMPORALE));
            }
            List<MisurazioneDTO> tutte = misurazioneRepository.findAll(filtro, Sort.by(Sort.Direction.ASC, "rilevatoIl"))
                    .stream()
                    .map(entita -> MisurazioneMapper.toDTO(entita, cacheNodi, cacheParcelle))
                    .toList();
            return new PageImpl<>(tutte, pageable, tutte.size());
        }

        Page<MisurazioneEntity> pagina = misurazioneRepository.findAll(filtro, pageable);
        return pagina.map(entita -> MisurazioneMapper.toDTO(entita, cacheNodi, cacheParcelle));
    }
}