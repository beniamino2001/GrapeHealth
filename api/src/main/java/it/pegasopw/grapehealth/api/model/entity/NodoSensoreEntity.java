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

    @Column(nullable = false)
    private String parcella;

    @Column(name = "tipo_nodo", nullable = false)
    private String tipoNodo;

    @Column
    private Double latitudine;

    @Column
    private Double longitudine;

    @Column(nullable = false)
    private boolean attivo;

    @Column(name = "data_installazione", nullable = false)
    private LocalDate dataInstallazione;

    public Long getId() { return id; }
    public String getCodice() { return codice; }
    public String getParcella() { return parcella; }
    public String getTipoNodo() { return tipoNodo; }
    public Double getLatitudine() { return latitudine; }
    public Double getLongitudine() { return longitudine; }
    public boolean isAttivo() { return attivo; }
    public LocalDate getDataInstallazione() { return dataInstallazione; }
}