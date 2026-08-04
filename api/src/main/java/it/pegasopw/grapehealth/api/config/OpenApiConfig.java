package it.pegasopw.grapehealth.api.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import org.springframework.context.annotation.Configuration;

@Configuration
@OpenAPIDefinition(info = @Info(
        title = "GrapeHealth API",
        version = "1.0",
        description = "Middleware IoT per la viticoltura di precisione: storico misurazioni, "
                + "allerte fitosanitarie/climatiche e raccomandazioni operative generate dal decision engine."
))
public class OpenApiConfig {
}