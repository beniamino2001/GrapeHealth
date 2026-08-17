package it.pegasopw.grapehealth.api.model.dto;

import java.time.LocalDate;

public record ParcellaDTO(
        String nome, String varieta, String coloreBacca,
        Double lunghezzaGermoglioCm, LocalDate germoglioAggiornatoIl,
        Double latitudine, Double longitudine, String note) {
}