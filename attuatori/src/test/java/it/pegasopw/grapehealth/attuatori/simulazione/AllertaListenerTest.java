package it.pegasopw.grapehealth.attuatori.simulazione;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import it.pegasopw.grapehealth.attuatori.listener.AllertaListener;
import it.pegasopw.grapehealth.attuatori.model.evento.AllertaEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class AllertaListenerTest {

    private ListAppender<ILoggingEvent> appender;
    private Logger logger;
    private final SimulatoreAttuazione simulatore = new SimulatoreAttuazione();
    private final AllertaListener listener = new AllertaListener(simulatore);

    @BeforeEach
    void setUp() {
        logger = (Logger) LoggerFactory.getLogger(AllertaListener.class);
        logger.setLevel(Level.TRACE); // indipendente da eventuali soglie impostate in application.yaml
        appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
    }

    @AfterEach
    void tearDown() {
        logger.detachAppender(appender);
    }

    @Test
    void loggaCampiStrutturatiAttesiPerStressIdricoSevero() {
        AllertaEvent evento = new AllertaEvent("stress_idrico", "severo", "idrico-A1",
                "parcellaA", "psi_stem", -1.5, "msg di test", Instant.now());

        listener.onAllerta(evento);

        assertEquals(1, appender.list.size());
        ILoggingEvent logEvent = appender.list.get(0);

        Map<String, String> keyValues = logEvent.getKeyValuePairs().stream()
                .collect(Collectors.toMap(kv -> kv.key, kv -> String.valueOf(kv.value)));

        assertEquals("stress_idrico", keyValues.get("tipo"));
        assertEquals("severo", keyValues.get("livelloRischio"));
        assertEquals("idrico-A1", keyValues.get("nodo"));
        assertEquals("parcellaA", keyValues.get("parcella"));
        assertEquals("irrigazione di soccorso d'emergenza", keyValues.get("azione"));
    }

    @Test
    void loggaAzioneModerataQuandoIlLivelloNonESevero() {
        AllertaEvent evento = new AllertaEvent("sunburn", "moderato", "bacca-B2",
                "parcellaB", "temperatura_bacca", 46.0, "msg di test", Instant.now());

        listener.onAllerta(evento);

        Map<String, String> keyValues = appender.list.get(0).getKeyValuePairs().stream()
                .collect(Collectors.toMap(kv -> kv.key, kv -> String.valueOf(kv.value)));

        assertEquals("nebulizzazione anti-scottatura", keyValues.get("azione"));
    }
}