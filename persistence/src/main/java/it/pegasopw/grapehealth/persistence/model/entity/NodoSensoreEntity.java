package it.pegasopw.grapehealth.persistence.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

// Sola lettura per questo modulo: la tabella e' popolata dal seed di
// 01_schema.sql e da sensors-simulator/scripts/init_nodi_db.py, non scritta
// da nessun listener qui. I setter esistono solo per costruire fixture nei
// test senza bisogno di un contesto di persistenza completo.
@Entity
@Table(name = "nodo_sensore")
public class NodoSensoreEntity {

    @Id
    private Long id;

    @Column(nullable = false, unique = true)
    private String codice;

    @Column(name = "tipo_nodo", nullable = false)
    private String tipoNodo;

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

    public String getTipoNodo() {
        return tipoNodo;
    }

    public void setTipoNodo(String tipoNodo) {
        this.tipoNodo = tipoNodo;
    }
}