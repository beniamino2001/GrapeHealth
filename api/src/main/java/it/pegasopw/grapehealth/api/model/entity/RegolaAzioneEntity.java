package it.pegasopw.grapehealth.api.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "regola_azione")
public class RegolaAzioneEntity {

    @Id
    private Long id;

    @Column(name = "regola_codice", nullable = false)
    private String regolaCodice;

    @Column(name = "azione_codice", nullable = false)
    private String azioneCodice;

    @Column
    private String note;

    public Long getId() { return id; }
    public String getRegolaCodice() { return regolaCodice; }
    public String getAzioneCodice() { return azioneCodice; }
    public String getNote() { return note; }
}