package it.pegasopw.grapehealth.api.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import it.pegasopw.grapehealth.api.model.dto.MisurazioneDTO;
import it.pegasopw.grapehealth.api.service.StoricoMisurazioniService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

@RestController
@RequestMapping("/api/misurazioni")
@Tag(name = "Misurazioni", description = "Storico delle misurazioni rilevate dai nodi sensore")
public class MisurazioneController {

    private final StoricoMisurazioniService storicoMisurazioniService;

    public MisurazioneController(StoricoMisurazioniService storicoMisurazioniService) {
        this.storicoMisurazioniService = storicoMisurazioniService;
    }

    @GetMapping
    @Operation(summary = "Storico misurazioni",
            description = "Restituisce le misurazioni filtrabili per parcella, parametro e intervallo temporale, paginate.")
    public Page<MisurazioneDTO> cerca(
            @Parameter(description = "Codice della parcella, es. 'parcellaA'")
            @RequestParam(required = false) String parcella,
            @Parameter(description = "Parametro rilevato, es. 'temperatura_aria', 'psi_stem'")
            @RequestParam(required = false) String parametro,
            @Parameter(description = "Istante iniziale dell'intervallo (ISO-8601)")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant dal,
            @Parameter(description = "Istante finale dell'intervallo (ISO-8601)")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant al,
            @PageableDefault(size = 50, sort = "rilevatoIl", direction = Sort.Direction.DESC) Pageable pageable) {
        return storicoMisurazioniService.cerca(parcella, parametro, dal, al, pageable);
    }
}