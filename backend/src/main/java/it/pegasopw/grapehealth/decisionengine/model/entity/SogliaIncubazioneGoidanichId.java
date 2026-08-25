package it.pegasopw.grapehealth.decisionengine.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.io.Serializable;
import java.util.Objects;

/**
 * Chiave composita di soglia_incubazione_goidanich: la tabella di Goidanich
 * non lega la velocità di incubazione a un valore continuo di umidità, ma
 * distingue solo due fasce (bassa/alta, oltre una soglia convenzionale) —
 * per questo umiditaAlta è un booleano e non una percentuale.
 */
@Embeddable
public class SogliaIncubazioneGoidanichId implements Serializable {

    @Column(name = "temperatura_media")
    private Integer temperaturaMedia;

    @Column(name = "umidita_alta")
    private Boolean umiditaAlta;

    public SogliaIncubazioneGoidanichId() {
    }

    public SogliaIncubazioneGoidanichId(Integer temperaturaMedia, Boolean umiditaAlta) {
        this.temperaturaMedia = temperaturaMedia;
        this.umiditaAlta = umiditaAlta;
    }

    public Integer getTemperaturaMedia() {
        return temperaturaMedia;
    }

    public void setTemperaturaMedia(Integer temperaturaMedia) {
        this.temperaturaMedia = temperaturaMedia;
    }

    public Boolean getUmiditaAlta() {
        return umiditaAlta;
    }

    public void setUmiditaAlta(Boolean umiditaAlta) {
        this.umiditaAlta = umiditaAlta;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SogliaIncubazioneGoidanichId that)) return false;
        return Objects.equals(temperaturaMedia, that.temperaturaMedia)
                && Objects.equals(umiditaAlta, that.umiditaAlta);
    }

    @Override
    public int hashCode() {
        return Objects.hash(temperaturaMedia, umiditaAlta);
    }
}