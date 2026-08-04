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

    @Column(nullable = false)
    private String descrizione;

    @Column(name = "regola_scatenante", nullable = false)
    private String regolaScatenante;

    @Column(name = "generata_il", nullable = false)
    private Instant generataIl;

    @Column(name = "risolta_il")
    private Instant risoltaIl;

    @Column(nullable = false)
    private String stato;

    public Long getId() { return id; }
    public String getTipo() { return tipo; }
    public String getLivelloRischio() { return livelloRischio; }
    public Long getNodoId() { return nodoId; }
    public String getDescrizione() { return descrizione; }
    public String getRegolaScatenante() { return regolaScatenante; }
    public Instant getGenerataIl() { return generataIl; }
    public Instant getRisoltaIl() { return risoltaIl; }
    public String getStato() { return stato; }
}