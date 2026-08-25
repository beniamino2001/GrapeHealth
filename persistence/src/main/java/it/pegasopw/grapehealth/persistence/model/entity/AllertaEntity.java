package it.pegasopw.grapehealth.persistence.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "allerta")
public class AllertaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String tipo;

    @Column(name = "livello_rischio", nullable = false)
    private String livelloRischio;

    @Column(name = "nodo_id")
    private Long nodoId;

    // Riferimento diretto alla parcella (regole valutate a livello di
    // parcella, non di singolo nodo, come "tre dieci"). Nullable come
    // nodo_id: una parcella non risolvibile non deve bloccare la scrittura
    // dell'allerta, e' un arricchimento, non un dato indispensabile.
    @Column(name = "parcella_id")
    private Long parcellaId;

    @Column(nullable = false)
    private String descrizione;

    // Foreign key verso regola(codice): usa lo stesso dominio di valori di
    // AllertaEvent.tipo(), es. "stress_idrico".
    @Column(name = "regola_codice", nullable = false)
    private String regolaCodice;

    @Column(name = "generata_il", nullable = false)
    private Instant generataIl;

    // Persiste la scadenza pianificata da SchedulerRisoluzioneAllerte, cosi'
    // che un riavvio del processo possa ricostruire le pianificazioni
    // pendenti leggendo dal database invece di perderle.
    @Column(name = "risoluzione_pianificata_il")
    private Instant risoluzionePianificataIl;

    @Column(name = "risolta_il")
    private Instant risoltaIl;

    // Il default e' impostato qui e non lasciato al DEFAULT del database:
    // Hibernate invia comunque un valore esplicito per ogni colonna mappata
    // in insert, quindi senza questo default andrebbe un NULL che viola il
    // vincolo NOT NULL della colonna.
    @Column
    private String stato = "attiva";

    public AllertaEntity() {
    }

    public AllertaEntity(String tipo, String livelloRischio, Long nodoId, Long parcellaId,
                         String descrizione, String regolaCodice, Instant generataIl) {
        this.tipo = tipo;
        this.livelloRischio = livelloRischio;
        this.nodoId = nodoId;
        this.parcellaId = parcellaId;
        this.descrizione = descrizione;
        this.regolaCodice = regolaCodice;
        this.generataIl = generataIl;
    }

    // Chiamato da SchedulerRisoluzioneAllerte subito dopo aver pianificato
    // la risoluzione: persiste la scadenza, così da poterla recuperare a un
    // eventuale riavvio del processo.
    public void pianificaRisoluzione(Instant risoluzionePianificataIl) {
        this.risoluzionePianificataIl = risoluzionePianificataIl;
    }

    public void risolvi(Instant risoltaIl) {
        this.stato = "risolta";
        this.risoltaIl = risoltaIl;
        this.risoluzionePianificataIl = null; // non più pendente
    }

    public Long getId() {
        return id;
    }

    public String getTipo() {
        return tipo;
    }

    public String getLivelloRischio() {
        return livelloRischio;
    }

    public Long getNodoId() {
        return nodoId;
    }

    public Long getParcellaId() {
        return parcellaId;
    }

    public String getDescrizione() {
        return descrizione;
    }

    public String getRegolaCodice() {
        return regolaCodice;
    }

    public Instant getGenerataIl() {
        return generataIl;
    }

    public Instant getRisoluzionePianificataIl() {
        return risoluzionePianificataIl;
    }

    public Instant getRisoltaIl() {
        return risoltaIl;
    }

    public String getStato() {
        return stato;
    }
}