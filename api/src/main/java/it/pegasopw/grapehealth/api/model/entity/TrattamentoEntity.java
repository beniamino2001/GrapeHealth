package it.pegasopw.grapehealth.api.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "trattamento")
public class TrattamentoEntity {

    @Id
    private Long id;

    @Column(name = "allerta_id", nullable = false)
    private Long allertaId;

    @Column(name = "tipo_azione", nullable = false)
    private String tipoAzione;

    @Column(name = "eseguito_il", nullable = false)
    private Instant eseguitoIl;

    @Column(nullable = false)
    private String esito;

    @Column
    private String note;

    public Long getId() { return id; }
    public Long getAllertaId() { return allertaId; }
    public String getTipoAzione() { return tipoAzione; }
    public Instant getEseguitoIl() { return eseguitoIl; }
    public String getEsito() { return esito; }
    public String getNote() { return note; }
}