package it.pegasopw.grapehealth.api.service;

import it.pegasopw.grapehealth.api.exception.ParametriNonValidiException;
import it.pegasopw.grapehealth.api.mapper.MisurazioneMapper;
import it.pegasopw.grapehealth.api.model.dto.MisurazioneDTO;
import it.pegasopw.grapehealth.api.model.entity.MisurazioneEntity;
import it.pegasopw.grapehealth.api.model.entity.NodoSensoreEntity;
import it.pegasopw.grapehealth.api.repository.MisurazioneRepository;
import it.pegasopw.grapehealth.api.repository.spec.MisurazioneSpecifications;
import it.pegasopw.grapehealth.api.service.support.NodoResolver;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Service
@Transactional(readOnly = true)
public class StoricoMisurazioniService {

    private final MisurazioneRepository misurazioneRepository;
    private final NodoResolver nodoResolver;

    public StoricoMisurazioniService(MisurazioneRepository misurazioneRepository, NodoResolver nodoResolver) {
        this.misurazioneRepository = misurazioneRepository;
        this.nodoResolver = nodoResolver;
    }

    public Page<MisurazioneDTO> cerca(String parcella, String parametro, Instant dal, Instant al, Pageable pageable) {
        if (dal != null && al != null && dal.isAfter(al)) {
            throw new ParametriNonValidiException("Il parametro 'dal' non puo' essere successivo al parametro 'al'.");
        }

        List<Long> nodoIds = nodoResolver.idsPerParcella(parcella);
        if (nodoIds != null && nodoIds.isEmpty()) {
            return Page.empty(pageable);
        }

        Specification<MisurazioneEntity> filtro = MisurazioneSpecifications.nodoIdIn(nodoIds)
                .and(MisurazioneSpecifications.parametro(parametro))
                .and(MisurazioneSpecifications.rilevatoIlDopo(dal))
                .and(MisurazioneSpecifications.rilevatoIlPrima(al));

        Page<MisurazioneEntity> pagina = misurazioneRepository.findAll(filtro, pageable);

        List<Long> nodoIdsPagina = pagina.getContent().stream()
                .map(MisurazioneEntity::getNodoId)
                .distinct()
                .toList();
        Map<Long, NodoSensoreEntity> nodiPerId = nodoResolver.caricaPerId(nodoIdsPagina);

        return pagina.map(entita -> MisurazioneMapper.toDTO(entita, nodiPerId));
    }
}