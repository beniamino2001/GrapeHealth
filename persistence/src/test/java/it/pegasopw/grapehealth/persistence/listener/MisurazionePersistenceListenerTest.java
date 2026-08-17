package it.pegasopw.grapehealth.persistence.listener;

import it.pegasopw.grapehealth.persistence.cache.CacheNodi;
import it.pegasopw.grapehealth.persistence.model.dto.MisurazioneMessage;
import it.pegasopw.grapehealth.persistence.model.entity.MisurazioneEntity;
import it.pegasopw.grapehealth.persistence.repository.MisurazioneRepository;
import it.pegasopw.grapehealth.persistence.simulazione.StimaScalaSimulazione;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class MisurazionePersistenceListenerTest {

    private static final String CODICE_NODO_DI_TEST = "idrico-A1";

    private Message misurazioneMessage(String nodo) {
        MessageProperties proprieta = new MessageProperties();
        proprieta.setReceivedRoutingKey("grapehealth.misurazioni." + nodo);
        return new Message("{}".getBytes(StandardCharsets.UTF_8), proprieta);
    }

    private MisurazioneMessage misurazioneDiTest(String nodo) {
        return new MisurazioneMessage(nodo, "parcellaA", "psi_stem", -1.2, "MPa", Instant.now());
    }

    @Test
    void unaSingolaMisurazioneNonRaggiungeIlBatchENonScriveSubito() {
        JsonMapper jsonMapper = mock(JsonMapper.class);
        MisurazioneRepository repository = mock(MisurazioneRepository.class);
        CacheNodi cacheNodi = mock(CacheNodi.class);
        StimaScalaSimulazione stimaScala = mock(StimaScalaSimulazione.class);
        when(jsonMapper.readValue(any(byte[].class), eq(MisurazioneMessage.class)))
                .thenReturn(misurazioneDiTest(CODICE_NODO_DI_TEST));
        when(cacheNodi.idPerCodice(CODICE_NODO_DI_TEST)).thenReturn(1L);

        MisurazionePersistenceListener listener =
                new MisurazionePersistenceListener(jsonMapper, repository, cacheNodi, stimaScala);
        listener.onMessage(misurazioneMessage(CODICE_NODO_DI_TEST));

        verifyNoInteractions(repository);
    }

    @Test
    void alRaggiungimentoDiCinquantaMisurazioniScriveUnUnicoBatchESvuotaIlBuffer() {
        JsonMapper jsonMapper = mock(JsonMapper.class);
        MisurazioneRepository repository = mock(MisurazioneRepository.class);
        CacheNodi cacheNodi = mock(CacheNodi.class);
        StimaScalaSimulazione stimaScala = mock(StimaScalaSimulazione.class);
        when(jsonMapper.readValue(any(byte[].class), eq(MisurazioneMessage.class)))
                .thenReturn(misurazioneDiTest(CODICE_NODO_DI_TEST));
        when(cacheNodi.idPerCodice(CODICE_NODO_DI_TEST)).thenReturn(1L);

        MisurazionePersistenceListener listener =
                new MisurazionePersistenceListener(jsonMapper, repository, cacheNodi, stimaScala);
        for (int i = 0; i < 50; i++) {
            listener.onMessage(misurazioneMessage(CODICE_NODO_DI_TEST));
        }

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<MisurazioneEntity>> captor = ArgumentCaptor.forClass(List.class);
        verify(repository, times(1)).saveAll(captor.capture());
        assertEquals(50, captor.getValue().size());

        // Il buffer e' stato svuotato dal batch precedente: il messaggio
        // successivo riparte da zero, nessuna seconda scrittura prematura.
        listener.onMessage(misurazioneMessage(CODICE_NODO_DI_TEST));
        verify(repository, times(1)).saveAll(any());
    }

    @Test
    void nodoSconosciutoScartaLaMisurazioneSenzaScrivere() {
        JsonMapper jsonMapper = mock(JsonMapper.class);
        MisurazioneRepository repository = mock(MisurazioneRepository.class);
        CacheNodi cacheNodi = mock(CacheNodi.class);
        StimaScalaSimulazione stimaScala = mock(StimaScalaSimulazione.class);
        when(jsonMapper.readValue(any(byte[].class), eq(MisurazioneMessage.class)))
                .thenReturn(misurazioneDiTest("nodo-fantasma"));
        when(cacheNodi.idPerCodice("nodo-fantasma")).thenReturn(null);

        MisurazionePersistenceListener listener =
                new MisurazionePersistenceListener(jsonMapper, repository, cacheNodi, stimaScala);
        listener.onMessage(misurazioneMessage("nodo-fantasma"));

        verifyNoInteractions(repository);
    }

    @Test
    void laStimaDiScalaVieneAggiornataAncheConNodoSconosciuto() {
        JsonMapper jsonMapper = mock(JsonMapper.class);
        MisurazioneRepository repository = mock(MisurazioneRepository.class);
        CacheNodi cacheNodi = mock(CacheNodi.class);
        StimaScalaSimulazione stimaScala = mock(StimaScalaSimulazione.class);
        MisurazioneMessage misurazione = misurazioneDiTest("nodo-fantasma");
        when(jsonMapper.readValue(any(byte[].class), eq(MisurazioneMessage.class))).thenReturn(misurazione);
        when(cacheNodi.idPerCodice("nodo-fantasma")).thenReturn(null);

        MisurazionePersistenceListener listener =
                new MisurazionePersistenceListener(jsonMapper, repository, cacheNodi, stimaScala);
        listener.onMessage(misurazioneMessage("nodo-fantasma"));

        verify(stimaScala).osserva(misurazione.timestampRilevazione());
    }

    @Test
    void ilFlushPeriodicoScriveIResiduiDelBufferESvuota() {
        JsonMapper jsonMapper = mock(JsonMapper.class);
        MisurazioneRepository repository = mock(MisurazioneRepository.class);
        CacheNodi cacheNodi = mock(CacheNodi.class);
        StimaScalaSimulazione stimaScala = mock(StimaScalaSimulazione.class);
        when(jsonMapper.readValue(any(byte[].class), eq(MisurazioneMessage.class)))
                .thenReturn(misurazioneDiTest(CODICE_NODO_DI_TEST));
        when(cacheNodi.idPerCodice(CODICE_NODO_DI_TEST)).thenReturn(1L);

        MisurazionePersistenceListener listener =
                new MisurazionePersistenceListener(jsonMapper, repository, cacheNodi, stimaScala);
        for (int i = 0; i < 10; i++) {
            listener.onMessage(misurazioneMessage(CODICE_NODO_DI_TEST));
        }
        verifyNoInteractions(repository);

        listener.flushPeriodico();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<MisurazioneEntity>> captor = ArgumentCaptor.forClass(List.class);
        verify(repository, times(1)).saveAll(captor.capture());
        assertEquals(10, captor.getValue().size());
    }

    @Test
    void ilFlushPeriodicoConBufferVuotoNonScriveNulla() {
        JsonMapper jsonMapper = mock(JsonMapper.class);
        MisurazioneRepository repository = mock(MisurazioneRepository.class);
        CacheNodi cacheNodi = mock(CacheNodi.class);
        StimaScalaSimulazione stimaScala = mock(StimaScalaSimulazione.class);

        MisurazionePersistenceListener listener =
                new MisurazionePersistenceListener(jsonMapper, repository, cacheNodi, stimaScala);
        listener.flushPeriodico();

        verifyNoInteractions(repository);
    }

    @Test
    void unMessaggioDiStatoNodoNonVieneTrattatoComeMisurazione() {
        JsonMapper jsonMapper = mock(JsonMapper.class);
        MisurazioneRepository repository = mock(MisurazioneRepository.class);
        CacheNodi cacheNodi = mock(CacheNodi.class);
        StimaScalaSimulazione stimaScala = mock(StimaScalaSimulazione.class);

        MessageProperties proprieta = new MessageProperties();
        proprieta.setReceivedRoutingKey("grapehealth.status.idrico-A1");
        Message messaggioDiStato = new Message("offline".getBytes(StandardCharsets.UTF_8), proprieta);

        MisurazionePersistenceListener listener =
                new MisurazionePersistenceListener(jsonMapper, repository, cacheNodi, stimaScala);
        listener.onMessage(messaggioDiStato);

        verifyNoInteractions(jsonMapper);
        verifyNoInteractions(repository);
        verifyNoInteractions(cacheNodi);
        verifyNoInteractions(stimaScala);
    }
}