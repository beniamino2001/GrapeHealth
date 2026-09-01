package it.pegasopw.grapehealth.api.config;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;

import java.io.IOException;

// Sovrascrive l'header Server aggiunto di default dal connettore Tomcat (che rivela versione
// e implementazione del servlet container) con un valore generico, sulla stessa risposta HTTP
// gia' in uscita: nessun impatto sul comportamento applicativo, solo sull'informazione esposta.
@Component
public class ServerHeaderFilter implements Filter {

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        if (response instanceof HttpServletResponse http) {
            http.setHeader("Server", "GrapeHealth");
        }
        chain.doFilter(request, response);
    }
}