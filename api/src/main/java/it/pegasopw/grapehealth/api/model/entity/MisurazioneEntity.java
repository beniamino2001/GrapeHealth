package it.pegasopw.grapehealth.api.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "misurazione")
public class MisurazioneEntity {

    @Id
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

    @Column(name = "ricevuto_il", nullable = false)
    private Instant ricevutoIl;

    public Long getId() { return id; }
    public Long getNodoId() { return nodoId; }
    public String getParametro() { return parametro; }
    public double getValore() { return valore; }
    public String getUnitaMisura() { return unitaMisura; }
    public Instant getRilevatoIl() { return rilevatoIl; }
    public Instant getRicevutoIl() { return ricevutoIl; }
}