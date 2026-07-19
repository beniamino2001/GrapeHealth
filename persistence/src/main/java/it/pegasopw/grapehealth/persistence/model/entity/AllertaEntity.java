package it.pegasopw.grapehealth.persistence.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "allerta")
public class AllertaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
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

    public AllertaEntity() {
    }

    public AllertaEntity(String tipo, String livelloRischio, Long nodoId, String descrizione,
                         String regolaScatenante, Instant generataIl) {
        this.tipo = tipo;
        this.livelloRischio = livelloRischio;
        this.nodoId = nodoId;
        this.descrizione = descrizione;
        this.regolaScatenante = regolaScatenante;
        this.generataIl = generataIl;
    }

    public Long getId() {
        return id;
    }

    public String getTipo() {
        return tipo;
    }

    public String getLivelloRischio() {
        return livelloRischio;
    }

    public Long getNodoId() {
        return nodoId;
    }

    public String getDescrizione() {
        return descrizione;
    }

    public String getRegolaScatenante() {
        return regolaScatenante;
    }

    public Instant getGenerataIl() {
        return generataIl;
    }
}