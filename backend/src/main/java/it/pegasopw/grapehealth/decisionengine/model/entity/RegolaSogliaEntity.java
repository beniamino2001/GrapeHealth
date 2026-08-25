package it.pegasopw.grapehealth.decisionengine.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Proiezione in sola lettura di una riga di regola_soglia: una singola
 * condizione soglia (parametro, livello di rischio, operatore, valore,
 * durata minima opzionale) appartenente a una regola. CacheSoglieRegole la
 * raggruppa per regola_codice all'avvio; ciascuna classe Regola* estrae da
 * quel gruppo esattamente i valori di cui ha bisogno.
 */
@Entity
@Table(name = "regola_soglia")
public class RegolaSogliaEntity {

    @Id
    private Long id;

    @Column(name = "regola_codice")
    private String regolaCodice;

    private String parametro;

    @Column(name = "livello_rischio")
    private String livelloRischio;

    private String operatore;

    @Column(name = "valore_soglia")
    private Double valoreSoglia;

    @Column(name = "durata_minima_minuti")
    private Integer durataMinimaMinuti;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getRegolaCodice() {
        return regolaCodice;
    }

    public void setRegolaCodice(String regolaCodice) {
        this.regolaCodice = regolaCodice;
    }

    public String getParametro() {
        return parametro;
    }

    public void setParametro(String parametro) {
        this.parametro = parametro;
    }

    public String getLivelloRischio() {
        return livelloRischio;
    }

    public void setLivelloRischio(String livelloRischio) {
        this.livelloRischio = livelloRischio;
    }

    public String getOperatore() {
        return operatore;
    }

    public void setOperatore(String operatore) {
        this.operatore = operatore;
    }

    public Double getValoreSoglia() {
        return valoreSoglia;
    }

    public void setValoreSoglia(Double valoreSoglia) {
        this.valoreSoglia = valoreSoglia;
    }

    public Integer getDurataMinimaMinuti() {
        return durataMinimaMinuti;
    }

    public void setDurataMinimaMinuti(Integer durataMinimaMinuti) {
        this.durataMinimaMinuti = durataMinimaMinuti;
    }
}