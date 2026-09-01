package it.pegasopw.grapehealth.decisionengine.listener;

import it.pegasopw.grapehealth.decisionengine.model.evento.AllertaEvent;
import it.pegasopw.grapehealth.decisionengine.publisher.AllertaPublisher;
import it.pegasopw.grapehealth.decisionengine.regole.RegolaRischio;
import it.pegasopw.grapehealth.decisionengine.stato.StatoRischio;
import it.pegasopw.grapehealth.decisionengine.cache.CacheNodiAttivi;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class MisurazioneListenerTest {

    private final JsonMapper jsonMapper = new JsonMapper();
    // Validator reale (non un mock): qui interessa il comportamento vero di
    // jakarta.validation sulle annotazioni di MisurazioneMessage, non uno
    // stub che risponderebbe sempre "nessuna violazione".
    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    private Message messaggio(String routingKey, String body) {
        MessageProperties proprieta = new MessageProperties();
        proprieta.setReceivedRoutingKey(routingKey);
        return new Message(body.getBytes(StandardCharsets.UTF_8), proprieta);
    }

    private String jsonMisurazione(String nodo, String parcella, String parametro, double valore) {
        return """
                {"nodo":"%s","parcella":"%s","parametro":"%s","valore":%s,\
                "unita_misura":"°C","timestamp_rilevazione":"2026-04-15T10:00:00Z"}""".formatted(
                nodo, parcella, parametro, valore);
    }

    private CacheNodiAttivi cacheNodiAttiviCheAccettaTutto() {
        CacheNodiAttivi cache = mock(CacheNodiAttivi.class);
        when(cache.attivo(any())).thenReturn(true);
        return cache;
    }

    @Test
    void nonTentaDiDeserializzareUnMessaggioDiStatoComeJson() {
        // corpo volutamente non-JSON: se il listener tentasse comunque di
        // deserializzarlo come misurazione, la chiamata sotto lancerebbe
        RegolaRischio regola = mock(RegolaRischio.class);
        AllertaPublisher publisher = mock(AllertaPublisher.class);
        MisurazioneListener listener = new MisurazioneListener(jsonMapper, List.of(regola), publisher,
                new StatoRischio(), cacheNodiAttiviCheAccettaTutto(), validator);

        listener.onMessage(messaggio("grapehealth.status.meteo-A1", "online"));

        verifyNoInteractions(regola, publisher);
    }

    @Test
    void deserializzaEValutaTutteLeRegoleIniettatePerUnaMisurazione() {
        RegolaRischio regolaCheScatta = mock(RegolaRischio.class);
        RegolaRischio regolaCheNonScatta = mock(RegolaRischio.class);
        AllertaEvent evento = new AllertaEvent("stress_idrico", "moderato", "idrico-A1", "parcellaA",
                "psi_stem", -1.3, "test", Instant.now());
        when(regolaCheScatta.valuta(any(), any())).thenReturn(Optional.of(evento));
        when(regolaCheNonScatta.valuta(any(), any())).thenReturn(Optional.empty());

        AllertaPublisher publisher = mock(AllertaPublisher.class);
        MisurazioneListener listener = new MisurazioneListener(jsonMapper,
                List.of(regolaCheScatta, regolaCheNonScatta), publisher, new StatoRischio(),
                cacheNodiAttiviCheAccettaTutto(), validator);

        listener.onMessage(messaggio("grapehealth.idrico.parcellaA.idrico-A1",
                jsonMisurazione("idrico-A1", "parcellaA", "psi_stem", -1.3)));

        verify(regolaCheScatta).valuta(any(), any());
        verify(regolaCheNonScatta).valuta(any(), any());
        verify(publisher).pubblica(evento);
        verifyNoMoreInteractions(publisher);
    }

    @Test
    void pubblicaUnaAllertaPerCiascunaRegolaCheScatta() {
        RegolaRischio prima = mock(RegolaRischio.class);
        RegolaRischio seconda = mock(RegolaRischio.class);
        AllertaEvent eventoPrima = new AllertaEvent("ondata_di_calore", "moderato", "meteo-A1", "parcellaA",
                "temperatura_aria", 36.0, "test", Instant.now());
        AllertaEvent eventoSeconda = new AllertaEvent("sunburn", "moderato", "bacca-A1", "parcellaA",
                "temperatura_bacca", 46.0, "test", Instant.now());
        when(prima.valuta(any(), any())).thenReturn(Optional.of(eventoPrima));
        when(seconda.valuta(any(), any())).thenReturn(Optional.of(eventoSeconda));

        AllertaPublisher publisher = mock(AllertaPublisher.class);
        MisurazioneListener listener = new MisurazioneListener(jsonMapper,
                List.of(prima, seconda), publisher, new StatoRischio(),
                cacheNodiAttiviCheAccettaTutto(), validator);

        listener.onMessage(messaggio("grapehealth.meteo.parcellaA.meteo-A1",
                jsonMisurazione("meteo-A1", "parcellaA", "temperatura_aria", 36.0)));

        verify(publisher).pubblica(eventoPrima);
        verify(publisher).pubblica(eventoSeconda);
    }

    @Test
    void propagaLEccezioneSuJsonMalformatoInveceDiIngoiarla() {
        // condizione necessaria perché il dead-letter configurato in
        // RabbitConfig possa scartare il messaggio invece di farlo
        // ripubblicare all'infinito dal container AMQP
        MisurazioneListener listener = new MisurazioneListener(jsonMapper, List.of(),
                mock(AllertaPublisher.class), new StatoRischio(),
                cacheNodiAttiviCheAccettaTutto(), validator);

        Message messaggioMalformato = messaggio("grapehealth.meteo.parcellaA.meteo-A1", "{questo non è JSON valido");

        assertThrows(RuntimeException.class, () -> listener.onMessage(messaggioMalformato));
    }

    @Test
    void riconosceUnSecondoMessaggioDiStatoConNodoDiverso() {
        RegolaRischio regola = mock(RegolaRischio.class);
        AllertaPublisher publisher = mock(AllertaPublisher.class);
        MisurazioneListener listener = new MisurazioneListener(jsonMapper, List.of(regola), publisher,
                new StatoRischio(), cacheNodiAttiviCheAccettaTutto(), validator);

        listener.onMessage(messaggio("grapehealth.status.bacca-C1", "offline"));

        verifyNoInteractions(regola, publisher);
    }

    // --- Filtro CacheNodiAttivi (v. §1.7 del recap di fase 3): la distinzione
    // a tre stati (attivo/disattivato/sconosciuto) non era mai stata
    // esercitata da un test unitario, solo verificata a mano sul log reale. ---

    @Test
    void scartaSilenziosamenteUnaMisurazioneDaNodoEsplicitamenteDisattivato() {
        RegolaRischio regola = mock(RegolaRischio.class);
        AllertaPublisher publisher = mock(AllertaPublisher.class);
        CacheNodiAttivi cacheNodiAttivi = mock(CacheNodiAttivi.class);
        when(cacheNodiAttivi.attivo("meteo-A1")).thenReturn(false);
        MisurazioneListener listener = new MisurazioneListener(jsonMapper, List.of(regola), publisher,
                new StatoRischio(), cacheNodiAttivi, validator);

        listener.onMessage(messaggio("grapehealth.meteo.parcellaA.meteo-A1",
                jsonMisurazione("meteo-A1", "parcellaA", "temperatura_aria", 36.0)));

        // Nessuna regola deve mai vedere questa misurazione: un nodo
        // esplicitamente rimosso dalla topologia non deve poter generare
        // allerte come se fosse ancora valido.
        verifyNoInteractions(regola, publisher);
    }

    @Test
    void elaboraNormalmenteUnaMisurazioneDaNodoSconosciutoInAnagrafica() {
        RegolaRischio regola = mock(RegolaRischio.class);
        when(regola.valuta(any(), any())).thenReturn(Optional.empty());
        AllertaPublisher publisher = mock(AllertaPublisher.class);
        CacheNodiAttivi cacheNodiAttivi = mock(CacheNodiAttivi.class);
        when(cacheNodiAttivi.attivo("meteo-X9")).thenReturn(null); // mai sincronizzato, non "disattivato"
        MisurazioneListener listener = new MisurazioneListener(jsonMapper, List.of(regola), publisher,
                new StatoRischio(), cacheNodiAttivi, validator);

        listener.onMessage(messaggio("grapehealth.meteo.parcellaA.meteo-X9",
                jsonMisurazione("meteo-X9", "parcellaA", "temperatura_aria", 36.0)));

        // Un nodo mai visto in anagrafica NON va confuso con uno disattivato:
        // la misurazione deve comunque raggiungere le regole.
        verify(regola).valuta(any(), any());
    }

    // --- Validazione (v. §1 punto (a) dell'audit): MisurazioneMessage ora
    // dichiara @NotBlank/@NotNull sui campi obbligatori; il listener deve
    // rifiutare esplicitamente un messaggio sintatticamente valido come JSON
    // ma con un campo obbligatorio mancante, invece di lasciarlo scivolare
    // dentro le regole con un valore nullo. ---

    @Test
    void propagaEccezioneSeUnCampoObbligatorioDellaMisurazioneManca() {
        MisurazioneListener listener = new MisurazioneListener(jsonMapper, List.of(),
                mock(AllertaPublisher.class), new StatoRischio(),
                cacheNodiAttiviCheAccettaTutto(), validator);

        // "nodo" assente: sintatticamente JSON valido, semanticamente incompleto
        String corpoConNodoMancante = """
                {"parcella":"parcellaA","parametro":"temperatura_aria","valore":36.0,\
                "unita_misura":"C","timestamp_rilevazione":"2026-04-15T10:00:00Z"}""";

        assertThrows(RuntimeException.class, () -> listener.onMessage(
                messaggio("grapehealth.meteo.parcellaA.meteo-A1", corpoConNodoMancante)));
    }

    @Test
    void propagaEccezioneSeIlNodoContieneUnCarattereCheAltererebbeLaRoutingKey() {
        // un punto in "nodo" spezzerebbe la struttura di "allerta.<tipo>.<parcella>.<nodo>"
        // pubblicata da AllertaPublisher, non solo il suo contenuto testuale
        MisurazioneListener listener = new MisurazioneListener(jsonMapper, List.of(),
                mock(AllertaPublisher.class), new StatoRischio(),
                cacheNodiAttiviCheAccettaTutto(), validator);

        assertThrows(RuntimeException.class, () -> listener.onMessage(
                messaggio("grapehealth.meteo.parcellaA.meteo-A1",
                        jsonMisurazione("meteo-A1.evil#", "parcellaA", "temperatura_aria", 36.0))));
    }
}