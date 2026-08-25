package it.pegasopw.grapehealth.api.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

public record AllertaDTO(
        Long id,
        @Schema(description = "'stress_idrico', 'ondata_di_calore', 'sunburn', 'tre_dieci', 'svernamento_oospore', 'infezione_secondaria' o 'danno_radicale'")
        String tipo,
        @Schema(description = "'moderato' o 'severo'")
        String livelloRischio,
        String nodoCodice,
        String parcella,
        String descrizione,
        // Nome campo invariato per continuita' con la dashboard, gia' scritta contro
        // questo contratto; internamente rispecchia allerta.regolaCodice.
        String regolaScatenante,
        Instant generataIl,
        @Schema(description = "Valorizzato solo quando lo stato passa a 'risolta'; null per le allerte ancora attive.")
        Instant risoltaIl,
        @Schema(description = "'attiva' o 'risolta'. Cambia autonomamente nel tempo: uno scheduler risolve le allerte attive al raggiungimento di risoluzionePianificataIl, non e' un'azione richiesta dal client.")
        String stato,
        @Schema(description = "Istante stimato di risoluzione automatica; puo' non coincidere con risoltaIl se lo scheduler gira con un ritardo rispetto alla pianificazione.")
        Instant risoluzionePianificataIl
) {
}