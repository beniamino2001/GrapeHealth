package it.pegasopw.grapehealth.api.service.support;

import it.pegasopw.grapehealth.api.model.entity.NodoSensoreEntity;
import it.pegasopw.grapehealth.api.repository.NodoSensoreRepository;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class NodoResolver {

    private final NodoSensoreRepository nodoSensoreRepository;

    public NodoResolver(NodoSensoreRepository nodoSensoreRepository) {
        this.nodoSensoreRepository = nodoSensoreRepository;
    }

    /**
     * Restituisce null se la parcella non è stata specificata (nessun filtro da
     * applicare) e una lista vuota se la parcella non esiste (nessun risultato).
     */
    public List<Long> idsPerParcella(String parcella) {
        if (parcella == null || parcella.isBlank()) {
            return null;
        }
        return nodoSensoreRepository.findByParcella(parcella).stream()
                .map(NodoSensoreEntity::getId)
                .toList();
    }

    public Map<Long, NodoSensoreEntity> caricaPerId(Collection<Long> nodoIds) {
        return nodoSensoreRepository.findAllById(nodoIds).stream()
                .collect(Collectors.toMap(NodoSensoreEntity::getId, Function.identity()));
    }
}