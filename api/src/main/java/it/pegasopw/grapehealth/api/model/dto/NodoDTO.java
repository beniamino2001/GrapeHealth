package it.pegasopw.grapehealth.api.model.dto;

import java.time.LocalDate;

public record NodoDTO(
        String codice, String tipoNodo, String parcella,
        boolean attivo, LocalDate dataInstallazione) {
}