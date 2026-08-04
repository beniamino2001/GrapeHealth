package it.pegasopw.grapehealth.api.service;

import it.pegasopw.grapehealth.api.mapper.AllertaMapper;
import it.pegasopw.grapehealth.api.model.dto.AllertaDTO;
import it.pegasopw.grapehealth.api.model.entity.AllertaEntity;
import it.pegasopw.grapehealth.api.model.entity.NodoSensoreEntity;
import it.pegasopw.grapehealth.api.repository.AllertaRepository;
import it.pegasopw.grapehealth.api.repository.spec.AllertaSpecifications;
import it.pegasopw.grapehealth.api.service.support.NodoResolver;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
@Transactional(readOnly = true)
public class AllerteService {

    private final AllertaRepository allertaRepository;
    private final NodoResolver nodoResolver;

    public AllerteService(AllertaRepository allertaRepository, NodoResolver nodoResolver) {
        this.allertaRepository = allertaRepository;
        this.nodoResolver = nodoResolver;
    }

    /**
     * Se stato non è specificato, di default restituisce solo le allerte "attiva" in quanto l'endpoint dell'API è pensato per il monitoraggio in tempo reale
     * e non come archivio storico completo.
     */
    public Page<AllertaDTO> cerca(String stato, String tipo, String parcella, Pageable pageable) {
        String statoEffettivo = (stato == null || stato.isBlank()) ? "attiva" : stato;

        List<Long> nodoIds = nodoResolver.idsPerParcella(parcella);
        if (nodoIds != null && nodoIds.isEmpty()) {
            return Page.empty(pageable);
        }

        Specification<AllertaEntity> filtro = AllertaSpecifications.stato(statoEffettivo)
                .and(AllertaSpecifications.tipo(tipo))
                .and(AllertaSpecifications.nodoIdIn(nodoIds));

        Page<AllertaEntity> pagina = allertaRepository.findAll(filtro, pageable);

        List<Long> nodoIdsPagina = pagina.getContent().stream()
                .map(AllertaEntity::getNodoId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        Map<Long, NodoSensoreEntity> nodiPerId = nodoResolver.caricaPerId(nodoIdsPagina);

        return pagina.map(entita -> AllertaMapper.toDTO(entita, nodiPerId));
    }
}