package it.pegasopw.grapehealth.persistence.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "misurazione")
public class MisurazioneEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "nodo_id", nullable = false)
    private Long nodoId;

    @Column(nullable = false)
    private String parametro;

    @Column(nullable = false)
    private double valore;

    @Column(name = "unita_misura", nullable = false)
    private String unitaMisura;

    @Column(name = "rilevato_il", nullable = false)
    private Instant rilevatoIl;

    public MisurazioneEntity() {
    }

    public MisurazioneEntity(Long nodoId, String parametro, double valore, String unitaMisura, Instant rilevatoIl) {
        this.nodoId = nodoId;
        this.parametro = parametro;
        this.valore = valore;
        this.unitaMisura = unitaMisura;
        this.rilevatoIl = rilevatoIl;
    }

    public Long getId() {
        return id;
    }

    public Long getNodoId() {
        return nodoId;
    }

    public String getParametro() {
        return parametro;
    }

    public double getValore() {
        return valore;
    }

    public String getUnitaMisura() {
        return unitaMisura;
    }

    public Instant getRilevatoIl() {
        return rilevatoIl;
    }
}