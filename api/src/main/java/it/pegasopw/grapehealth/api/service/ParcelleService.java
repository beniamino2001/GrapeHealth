package it.pegasopw.grapehealth.api.service;

import it.pegasopw.grapehealth.api.cache.CacheParcelle;
import it.pegasopw.grapehealth.api.exception.RisorsaNonTrovataException;
import it.pegasopw.grapehealth.api.mapper.ParcellaMapper;
import it.pegasopw.grapehealth.api.model.dto.ParcellaDTO;
import it.pegasopw.grapehealth.api.model.entity.ParcellaEntity;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ParcelleService {
    private final CacheParcelle cacheParcelle;

    public ParcelleService(CacheParcelle cacheParcelle) { this.cacheParcelle = cacheParcelle; }

    public List<ParcellaDTO> tutte() {
        return cacheParcelle.tutte().stream().map(ParcellaMapper::toDTO).toList();
    }

    public ParcellaDTO perNome(String nome) {
        ParcellaEntity p = cacheParcelle.trovaPerNome(nome);
        if (p == null) throw new RisorsaNonTrovataException("Nessuna parcella trovata con nome " + nome);
        return ParcellaMapper.toDTO(p);
    }
}