package it.pegasopw.grapehealth.decisionengine.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/**
 * Mappatura in sola lettura della tabella soglia_incubazione_goidanich: una
 * riga per ogni combinazione nota di temperatura media giornaliera e livello
 * di umidità, con la percentuale di sviluppo dell'incubazione della
 * peronospora attesa in quel giorno secondo il modello di Goidanich. Caricata
 * per intero all'avvio da CacheTabellaGoidanich, che la tiene in memoria come
 * tabella indicizzata per temperatura.
 */
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