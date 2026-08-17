package it.pegasopw.grapehealth.api.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "regola_soglia")
public class RegolaSogliaEntity {

    @Id
    private Long id;

    @Column(name = "regola_codice", nullable = false)
    private String regolaCodice;

    @Column(nullable = false)
    private String parametro;

    @Column(name = "livello_rischio", nullable = false)
    private String livelloRischio;

    @Column(nullable = false)
    private String operatore;

    @Column(name = "valore_soglia", nullable = false)
    private double valoreSoglia;

    @Column(name = "unita_misura", nullable = false)
    private String unitaMisura;

    @Column(name = "durata_minima_minuti")
    private Integer durataMinimaMinuti;

    @Column
    private String note;

    public Long getId() { return id; }
    public String getRegolaCodice() { return regolaCodice; }
    public String getParametro() { return parametro; }
    public String getLivelloRischio() { return livelloRischio; }
    public String getOperatore() { return operatore; }
    public double getValoreSoglia() { return valoreSoglia; }
    public String getUnitaMisura() { return unitaMisura; }
    public Integer getDurataMinimaMinuti() { return durataMinimaMinuti; }
    public String getNote() { return note; }
}