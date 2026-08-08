package it.pegasopw.grapehealth.decisionengine.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Proiezione minima e in sola lettura della tabella "parcella": il decision
 * engine non scrive mai su questa tabella, la legge solo per popolare
 * CacheGermogli. Non mappa tutte le colonne della tabella (varieta, colore_bacca, coordinate, ecc.), 
 * solo quelle che servono qui (con ddl-auto=validate, Hibernate valida che le colonne mappate esistano e
 * siano compatibili, non richiede una mappatura completa della tabella).
 */
@Entity
@Table(name = "parcella")
public class ParcellaEntity {

    @Id
    private Long id;

    @Column(nullable = false, unique = true)
    private String nome;

    @Column(name = "lunghezza_germoglio_cm")
    private Double lunghezzaGermoglioCm;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getNome() {
        return nome;
    }

    public void setNome(String nome) {
        this.nome = nome;
    }

    public Double getLunghezzaGermoglioCm() {
        return lunghezzaGermoglioCm;
    }

    public void setLunghezzaGermoglioCm(Double lunghezzaGermoglioCm) {
        this.lunghezzaGermoglioCm = lunghezzaGermoglioCm;
    }
}