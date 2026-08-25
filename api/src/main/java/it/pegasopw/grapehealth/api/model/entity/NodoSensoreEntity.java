package it.pegasopw.grapehealth.api.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDate;

@Entity
@Table(name = "nodo_sensore")
public class NodoSensoreEntity {

    @Id
    private Long id;

    @Column(nullable = false, unique = true)
    private String codice;
    
    // FK verso il catalogo parcella. Risolta in nome tramite CacheParcelle.
    @Column(name = "parcella_id", nullable = false)
    private Long parcellaId;

    @Column(name = "tipo_nodo", nullable = false)
    private String tipoNodo;

    // Scritto e mantenuto da uno script di sincronizzazione esterno (init_nodi_db.py): un nodo
    // rimosso dalla configurazione viene marcato attivo=false, non cancellato. Il decision engine
    // lo usa gia' per escludere le misurazioni di nodi disattivati dalla valutazione delle regole;
    // qui viene solo mostrato, non applicato ad alcuna logica di filtro.
    @Column(nullable = false)
    private boolean attivo;

    // Puro metadato di inventario, nessuna regola bibliografica lo consulta: utile solo per
    // contesto operativo, da quanto tempo un nodo è in servizio.
    @Column(name = "data_installazione", nullable = false)
    private LocalDate dataInstallazione;

    public Long getId() { return id; }
    public String getCodice() { return codice; }
    public Long getParcellaId() { return parcellaId; }
    public String getTipoNodo() { return tipoNodo; }
    public boolean isAttivo() { return attivo; }
    public LocalDate getDataInstallazione() { return dataInstallazione; }
}