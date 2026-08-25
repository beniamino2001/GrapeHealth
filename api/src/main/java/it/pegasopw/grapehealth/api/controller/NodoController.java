package it.pegasopw.grapehealth.api.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import it.pegasopw.grapehealth.api.model.dto.NodoDTO;
import it.pegasopw.grapehealth.api.service.NodiService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/nodi")
@Tag(name = "Nodi", description = "Anagrafica dei nodi sensore: tipo, parcella di appartenenza, stato operativo e data di installazione")
public class NodoController {
    private final NodiService nodiService;

    public NodoController(NodiService nodiService) { this.nodiService = nodiService; }

    @GetMapping
    @Operation(summary = "Elenco dei nodi sensore")
    public List<NodoDTO> tutti() { return nodiService.tutti(); }

    @GetMapping("/{codice}")
    @Operation(summary = "Dettaglio di un nodo sensore", description = "404 se il codice non e' noto.")
    public NodoDTO perCodice(@Parameter(description = "es. 'meteo-A1'") @PathVariable String codice) {
        return nodiService.perCodice(codice);
    }
}