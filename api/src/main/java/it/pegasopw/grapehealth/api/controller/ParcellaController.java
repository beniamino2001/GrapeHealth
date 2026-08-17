package it.pegasopw.grapehealth.api.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import it.pegasopw.grapehealth.api.model.dto.ParcellaDTO;
import it.pegasopw.grapehealth.api.service.ParcelleService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/parcelle")
@Tag(name = "Parcelle", description = "Catalogo agronomico: varieta', colore bacca, dato fenologico del germoglio richiamato dalle soglie tre_dieci")
public class ParcellaController {
    private final ParcelleService parcelleService;

    public ParcellaController(ParcelleService parcelleService) { this.parcelleService = parcelleService; }

    @GetMapping
    @Operation(summary = "Elenco delle parcelle")
    public List<ParcellaDTO> tutte() { return parcelleService.tutte(); }

    @GetMapping("/{nome}")
    @Operation(summary = "Dettaglio di una parcella", description = "404 se il nome non e' noto.")
    public ParcellaDTO perNome(@Parameter(description = "es. 'parcellaA'") @PathVariable String nome) {
        return parcelleService.perNome(nome);
    }
}