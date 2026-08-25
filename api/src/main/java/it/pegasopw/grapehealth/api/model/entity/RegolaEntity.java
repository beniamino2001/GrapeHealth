package it.pegasopw.grapehealth.api.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "regola")
public class RegolaEntity {

    @Id
    private String codice;

    @Column(nullable = false)
    private String descrizione;

    @Column(name = "fonte_bibliografica", nullable = false)
    private String fonteBibliografica;

    public String getCodice() { return codice; }
    public String getDescrizione() { return descrizione; }
    public String getFonteBibliografica() { return fonteBibliografica; }
}