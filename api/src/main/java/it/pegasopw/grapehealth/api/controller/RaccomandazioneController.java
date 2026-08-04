package it.pegasopw.grapehealth.api.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import it.pegasopw.grapehealth.api.model.dto.RaccomandazioneDTO;
import it.pegasopw.grapehealth.api.service.RaccomandazioniService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/raccomandazioni")
@Tag(name = "Raccomandazioni", description = "Raccomandazioni rule-based, eventualmente arricchite dall'esecuzione simulata")
public class RaccomandazioneController {

    private final RaccomandazioniService raccomandazioniService;

    public RaccomandazioneController(RaccomandazioniService raccomandazioniService) {
        this.raccomandazioniService = raccomandazioniService;
    }

    @GetMapping
    @Operation(summary = "Raccomandazioni per una singola allerta o per tutte le allerte attive",
            description = "Se 'allertaId' e' specificato restituisce la raccomandazione per quella allerta; altrimenti l'elenco per tutte le allerte attualmente attive.")
    public List<RaccomandazioneDTO> cerca(
            @Parameter(description = "Id dell'allerta specifica. Se omesso, restituisce le raccomandazioni per tutte le allerte attive")
            @RequestParam(required = false) Long allertaId) {
        if (allertaId != null) {
            return List.of(raccomandazioniService.perAllerta(allertaId));
        }
        return raccomandazioniService.perAllerteAttive();
    }
}