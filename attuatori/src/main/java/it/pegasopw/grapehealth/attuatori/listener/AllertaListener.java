package it.pegasopw.grapehealth.attuatori.listener;

import it.pegasopw.grapehealth.attuatori.config.RabbitConfig;
import it.pegasopw.grapehealth.attuatori.model.evento.AllertaEvent;
import it.pegasopw.grapehealth.attuatori.simulazione.SimulatoreAttuazione;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class AllertaListener {

    private static final Logger log = LoggerFactory.getLogger(AllertaListener.class);
    private final SimulatoreAttuazione simulatore;

    public AllertaListener(SimulatoreAttuazione simulatore) {
        this.simulatore = simulatore;
    }

    @RabbitListener(queues = RabbitConfig.INPUT_QUEUE)
    public void onAllerta(AllertaEvent evento) {
        String azione = simulatore.determinaAzione(evento);

        // parametro/valoreOsservato/messaggio, non solo tipo e livello: senza
        // questi campi il log risponderebbe solo a "che cosa e' scattato", non
        // a "perche'" - quale grandezza, quale valore misurato.
        log.atInfo()
                .addKeyValue("tipo", evento.tipo())
                .addKeyValue("livelloRischio", evento.livelloRischio())
                .addKeyValue("nodo", evento.nodo())
                .addKeyValue("parcella", evento.parcella())
                .addKeyValue("parametro", evento.parametro())
                .addKeyValue("valoreOsservato", evento.valoreOsservato())
                .addKeyValue("azione", azione)
                .addKeyValue("messaggio", evento.messaggio())
                .addKeyValue("timestampAllerta", evento.timestamp().toString())
                .log("Attuazione simulata");
    }
}