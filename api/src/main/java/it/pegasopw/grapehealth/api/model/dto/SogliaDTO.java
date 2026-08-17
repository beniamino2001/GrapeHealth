package it.pegasopw.grapehealth.api.model.dto;

public record SogliaDTO(
        String parametro,
        String livelloRischio,
        String operatore,
        double valoreSoglia,
        String unitaMisura,
        Integer durataMinimaMinuti,
        String note
) {
}