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
    @Operation(summary = "Raccomandazioni per una singola allerta, per un insieme di allerte, o per tutte le allerte attive",
            description = "Se 'allertaId' e' specificato restituisce la raccomandazione per quella allerta (404 se non esiste). " +
                    "Se 'allertaIds' e' specificato (parametro ripetuto) restituisce le raccomandazioni per l'insieme richiesto, " +
                    "ignorando silenziosamente gli id non trovati. Se nessuno dei due e' specificato, restituisce l'elenco per tutte le allerte attualmente attive.")
    public List<RaccomandazioneDTO> cerca(
            @Parameter(description = "Id di una singola allerta. Se omesso, vedi 'allertaIds'")
            @RequestParam(required = false) Long allertaId,
            @Parameter(description = "Elenco di id di allerta (?allertaIds=1&allertaIds=2&...). Recupera in una sola chiamata le raccomandazioni per un insieme gia' noto (es. le ultime N risolte), evitando una richiesta per ciascuna.")
            @RequestParam(required = false) List<Long> allertaIds) {
        if (allertaId != null) {
            return List.of(raccomandazioniService.perAllerta(allertaId));
        }
        if (allertaIds != null && !allertaIds.isEmpty()) {
            return raccomandazioniService.perAllerteMultiple(allertaIds);
        }
        return raccomandazioniService.perAllerteAttive();
    }
}