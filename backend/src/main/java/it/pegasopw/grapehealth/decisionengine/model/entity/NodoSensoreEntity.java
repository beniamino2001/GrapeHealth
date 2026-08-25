package it.pegasopw.grapehealth.decisionengine.model.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Proiezione in sola lettura di nodo_sensore: solo il codice e lo stato
 * attivo, gli unici due campi che questa fase consulta. CacheNodiAttivi la
 * usa per costruire, all'avvio, l'anagrafica dei nodi correnti.
 */
@Entity
@Table(name = "nodo_sensore")
public class NodoSensoreEntity {

    @Id
    private Long id;

    private String codice;

    private Boolean attivo;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getCodice() {
        return codice;
    }

    public void setCodice(String codice) {
        this.codice = codice;
    }

    public Boolean getAttivo() {
        return attivo;
    }

    public void setAttivo(Boolean attivo) {
        this.attivo = attivo;
    }
}