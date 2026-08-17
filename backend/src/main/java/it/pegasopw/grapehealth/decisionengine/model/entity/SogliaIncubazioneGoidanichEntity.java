package it.pegasopw.grapehealth.decisionengine.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "soglia_incubazione_goidanich")
public class SogliaIncubazioneGoidanichEntity {

    @EmbeddedId
    private SogliaIncubazioneGoidanichId id;

    @Column(name = "percentuale_incremento_giornaliero", nullable = false)
    private Double percentualeIncrementoGiornaliero;

    public SogliaIncubazioneGoidanichId getId() {
        return id;
    }

    public void setId(SogliaIncubazioneGoidanichId id) {
        this.id = id;
    }

    public Double getPercentualeIncrementoGiornaliero() {
        return percentualeIncrementoGiornaliero;
    }

    public void setPercentualeIncrementoGiornaliero(Double percentualeIncrementoGiornaliero) {
        this.percentualeIncrementoGiornaliero = percentualeIncrementoGiornaliero;
    }
}