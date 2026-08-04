package it.pegasopw.grapehealth.api.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import it.pegasopw.grapehealth.api.model.dto.AllertaDTO;
import it.pegasopw.grapehealth.api.service.AllerteService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/allerte")
@Tag(name = "Allerte", description = "Allerte generate dal decision engine (fitosanitarie e climatiche)")
public class AllertaController {

    private final AllerteService allerteService;

    public AllertaController(AllerteService allerteService) {
        this.allerteService = allerteService;
    }

    @GetMapping
    @Operation(summary = "Elenco allerte",
            description = "Filtrabile per stato, tipo e parcella. Se 'stato' e' omesso, di default restituisce solo le allerte attive.")
    public Page<AllertaDTO> cerca(
            @Parameter(description = "'attiva' o 'risolta'. Default: 'attiva' se omesso")
            @RequestParam(required = false) String stato,
            @Parameter(description = "'stress_idrico', 'ondata_di_calore', 'sunburn' o 'tre_dieci'")
            @RequestParam(required = false) String tipo,
            @Parameter(description = "Codice della parcella, es. 'parcellaA'")
            @RequestParam(required = false) String parcella,
            @PageableDefault(size = 50, sort = "generataIl", direction = Sort.Direction.DESC) Pageable pageable) {
        return allerteService.cerca(stato, tipo, parcella, pageable);
    }
}