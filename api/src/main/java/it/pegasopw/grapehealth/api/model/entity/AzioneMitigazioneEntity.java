package it.pegasopw.grapehealth.api.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "azione_mitigazione")
public class AzioneMitigazioneEntity {

    @Id
    private String codice;

    @Column(nullable = false)
    private String descrizione;

    @Column(name = "fonte_bibliografica")
    private String fonteBibliografica;

    public String getCodice() { return codice; }
    public String getDescrizione() { return descrizione; }
    public String getFonteBibliografica() { return fonteBibliografica; }
}