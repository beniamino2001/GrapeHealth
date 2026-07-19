package it.pegasopw.grapehealth.persistence.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "trattamento")
public class TrattamentoEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "allerta_id")
    private Long allertaId;

    @Column(name = "tipo_azione", nullable = false)
    private String tipoAzione;

    @Column
    private String note;

    public TrattamentoEntity() {
    }

    public TrattamentoEntity(Long allertaId, String tipoAzione, String note) {
        this.allertaId = allertaId;
        this.tipoAzione = tipoAzione;
        this.note = note;
    }

    public Long getId() {
        return id;
    }

    public Long getAllertaId() {
        return allertaId;
    }

    public String getTipoAzione() {
        return tipoAzione;
    }

    public String getNote() {
        return note;
    }
}