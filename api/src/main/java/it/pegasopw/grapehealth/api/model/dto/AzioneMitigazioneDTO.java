package it.pegasopw.grapehealth.api.model.dto;

public record AzioneMitigazioneDTO(
        String codice,
        String descrizione,
        String fonteBibliografica,
        String nota
) {
}