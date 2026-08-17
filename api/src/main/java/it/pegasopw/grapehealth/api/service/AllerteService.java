package it.pegasopw.grapehealth.api.service;

import it.pegasopw.grapehealth.api.cache.CacheNodi;
import it.pegasopw.grapehealth.api.cache.CacheParcelle;
import it.pegasopw.grapehealth.api.mapper.AllertaMapper;
import it.pegasopw.grapehealth.api.model.dto.AllertaDTO;
import it.pegasopw.grapehealth.api.model.entity.AllertaEntity;
import it.pegasopw.grapehealth.api.repository.AllertaRepository;
import it.pegasopw.grapehealth.api.repository.spec.AllertaSpecifications;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class AllerteService {

    private final AllertaRepository allertaRepository;
    private final CacheNodi cacheNodi;
    private final CacheParcelle cacheParcelle;

    public AllerteService(AllertaRepository allertaRepository, CacheNodi cacheNodi, CacheParcelle cacheParcelle) {
        this.allertaRepository = allertaRepository;
        this.cacheNodi = cacheNodi;
        this.cacheParcelle = cacheParcelle;
    }

    /**
     * Se stato non e' specificato, di default restituisce solo le allerte "attiva".
     * Il filtro per parcella non passa piu' dal nodo: allerta.parcella_id è un riferimento diretto.
     */
    public Page<AllertaDTO> cerca(String stato, String tipo, String parcella, Pageable pageable) {
        String statoEffettivo = (stato == null || stato.isBlank()) ? "attiva" : stato;

        Long parcellaId = null;
        if (parcella != null && !parcella.isBlank()) {
            parcellaId = cacheParcelle.idPerNome(parcella);
            if (parcellaId == null) {
                return Page.empty(pageable);
            }
        }

        Specification<AllertaEntity> filtro = AllertaSpecifications.stato(statoEffettivo)
                .and(AllertaSpecifications.tipo(tipo))
                .and(AllertaSpecifications.parcellaId(parcellaId));

        Page<AllertaEntity> pagina = allertaRepository.findAll(filtro, pageable);

        return pagina.map(entita -> AllertaMapper.toDTO(entita, cacheNodi, cacheParcelle));
    }
}