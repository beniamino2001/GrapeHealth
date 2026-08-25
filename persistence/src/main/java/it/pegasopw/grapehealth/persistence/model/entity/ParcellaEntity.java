package it.pegasopw.grapehealth.persistence.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

// Sola lettura, proiezione minima: la tabella e' popolata dal seed SQL
// (01_schema.sql), non scritta da questo modulo. Mappati solo i campi che
// servono a CacheParcelle per risolvere evento.parcella() (il nome) in un
// id. I setter esistono solo per costruire fixture nei test.
@Entity
@Table(name = "parcella")
public class ParcellaEntity {

    @Id
    private Long id;

    @Column(nullable = false, unique = true)
    private String nome;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getNome() {
        return nome;
    }

    public void setNome(String nome) {
        this.nome = nome;
    }
}