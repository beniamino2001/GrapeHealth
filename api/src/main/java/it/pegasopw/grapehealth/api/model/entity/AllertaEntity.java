package it.pegasopw.grapehealth.api.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "allerta")
public class AllertaEntity {

    @Id
    private Long id;

    @Column(nullable = false)
    private String tipo;

    @Column(name = "livello_rischio", nullable = false)
    private String livelloRischio;

    @Column(name = "nodo_id")
    private Long nodoId;
    
    // Riferimento diretto alla parcella, non derivato dal nodo fisico "meteo"
    // usato come proxy. Nullable: una parcella non risolvibile non blocca la
    // scrittura lato persistence.
    @Column(name = "parcella_id")
    private Long parcellaId;

    @Column(nullable = false)
    private String descrizione;

    // Vera FK verso regola(codice). Il campo Java segue il nome della colonna;
    // il nome esposto in AllertaDTO resta invece "regolaScatenante" per
    // continuita' con la dashboard, gia' scritta contro quel contratto (v.
    // AllertaMapper).
    @Column(name = "regola_codice", nullable = false)
    private String regolaCodice;

    @Column(name = "generata_il", nullable = false)
    private Instant generataIl;

    @Column(name = "risoluzione_pianificata_il")
    private Instant risoluzionePianificataIl;

    @Column(name = "risolta_il")
    private Instant risoltaIl;

    @Column(nullable = false)
    private String stato;

    public Long getId() { return id; }
    public String getTipo() { return tipo; }
    public String getLivelloRischio() { return livelloRischio; }
    public Long getNodoId() { return nodoId; }
    public Long getParcellaId() { return parcellaId; }
    public String getDescrizione() { return descrizione; }
    public String getRegolaCodice() { return regolaCodice; }
    public Instant getGenerataIl() { return generataIl; }
    public Instant getRisoluzionePianificataIl() { return risoluzionePianificataIl; }
    public Instant getRisoltaIl() { return risoltaIl; }
    public String getStato() { return stato; }
}