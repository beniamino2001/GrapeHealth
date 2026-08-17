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
    
    @Column(name = "parcella_id")
    private Long parcellaId;

    @Column(nullable = false)
    private String descrizione;

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