-- GrapeHealth: schema DB di riferimento per la persistenza dei dati dei sensori e delle allerte

CREATE TABLE nodo_sensore (
    id              BIGSERIAL PRIMARY KEY,
    codice          VARCHAR(64) NOT NULL UNIQUE,       -- es. "parcellaA-nodo3"
    parcella        VARCHAR(64) NOT NULL,
    tipo_nodo       VARCHAR(32) NOT NULL,               -- es. "meteo", "suolo", "bacca"
    latitudine      DOUBLE PRECISION,
    longitudine     DOUBLE PRECISION,
    attivo          BOOLEAN NOT NULL DEFAULT TRUE,
    data_installazione DATE NOT NULL DEFAULT CURRENT_DATE
);

CREATE TABLE misurazione (
    id              BIGSERIAL PRIMARY KEY,
    nodo_id         BIGINT NOT NULL REFERENCES nodo_sensore(id),
    parametro       VARCHAR(32) NOT NULL,   -- es. "temperatura_aria", "psi_stem", "bagnatura_fogliare"
    valore          DOUBLE PRECISION NOT NULL,
    unita_misura    VARCHAR(16) NOT NULL,   -- es. "C", "MPa", "%"
    rilevato_il     TIMESTAMPTZ NOT NULL,
    ricevuto_il     TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_misurazione_nodo_tempo ON misurazione (nodo_id, rilevato_il DESC);
CREATE INDEX idx_misurazione_parametro ON misurazione (parametro, rilevato_il DESC);

CREATE TABLE allerta (
    id                  BIGSERIAL PRIMARY KEY,
    tipo                VARCHAR(32) NOT NULL,   -- "fitosanitaria" | "stress_idrico" | "sunburn"
    livello_rischio     VARCHAR(16) NOT NULL,   -- "moderato" | "severo"
    nodo_id             BIGINT REFERENCES nodo_sensore(id),
    descrizione         TEXT NOT NULL,
    regola_scatenante   VARCHAR(64) NOT NULL,   -- riferimento alla regola del decision engine, es. "tre_dieci", "psi_stem_1_2"
    generata_il         TIMESTAMPTZ NOT NULL DEFAULT now(),
    risolta_il          TIMESTAMPTZ,
    stato               VARCHAR(16) NOT NULL DEFAULT 'attiva'  -- "attiva" | "risolta"
);

CREATE INDEX idx_allerta_stato ON allerta (stato, generata_il DESC);

CREATE TABLE trattamento (
    id              BIGSERIAL PRIMARY KEY,
    allerta_id      BIGINT REFERENCES allerta(id),
    tipo_azione     VARCHAR(32) NOT NULL,   -- "trattamento_fitosanitario" | "nebulizzazione" | "irrigazione_soccorso"
    eseguito_il     TIMESTAMPTZ NOT NULL DEFAULT now(),
    esito           VARCHAR(16) NOT NULL DEFAULT 'simulato',
    note            TEXT
);