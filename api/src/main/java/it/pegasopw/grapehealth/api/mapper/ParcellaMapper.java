package it.pegasopw.grapehealth.api.mapper;

import it.pegasopw.grapehealth.api.model.dto.ParcellaDTO;
import it.pegasopw.grapehealth.api.model.entity.ParcellaEntity;

public final class ParcellaMapper {
    private ParcellaMapper() {}

    public static ParcellaDTO toDTO(ParcellaEntity e) {
        return new ParcellaDTO(e.getNome(), e.getVarieta(), e.getColoreBacca(),
                e.getLunghezzaGermoglioCm(), e.getGermoglioAggiornatoIl(),
                e.getLatitudine(), e.getLongitudine(), e.getNote());
    }
}