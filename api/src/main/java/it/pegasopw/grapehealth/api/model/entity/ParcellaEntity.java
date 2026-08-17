package it.pegasopw.grapehealth.api.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDate;

@Entity
@Table(name = "parcella")
public class ParcellaEntity {

    @Id
    private Long id;

    @Column(nullable = false, unique = true)
    private String nome;

    @Column(nullable = false)
    private String varieta;

    @Column(name = "colore_bacca", nullable = false)
    private String coloreBacca;

    @Column(name = "lunghezza_germoglio_cm")
    private Double lunghezzaGermoglioCm;

    @Column(name = "germoglio_aggiornato_il")
    private LocalDate germoglioAggiornatoIl;

    @Column
    private Double latitudine;

    @Column
    private Double longitudine;

    @Column
    private String note;

    public Long getId() { return id; }
    public String getNome() { return nome; }
    public String getVarieta() { return varieta; }
    public String getColoreBacca() { return coloreBacca; }
    public Double getLunghezzaGermoglioCm() { return lunghezzaGermoglioCm; }
    public LocalDate getGermoglioAggiornatoIl() { return germoglioAggiornatoIl; }
    public Double getLatitudine() { return latitudine; }
    public Double getLongitudine() { return longitudine; }
    public String getNote() { return note; }
}