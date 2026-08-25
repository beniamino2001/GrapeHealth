-- GrapeHealth: schema DB
--

-- =====================================================================
-- Anagrafica agronomica
-- =====================================================================

-- Prima "parcella" era una VARCHAR libera duplicata su ogni riga di nodo_sensore,
-- senza alcun posto dove tenere gli attributi della parcella stessa. In particolare
-- la lunghezza del germoglio richiesta dalla regola dei "tre dieci" era hardcoded
-- in una Map<String,Double> nel codice Java del decision engine (RegolaTreDieci):
-- un dato agronomico rilevato manualmente a sopralluogo, non da un sensore, ma comunque
-- un'informazione che ha senso che viva nel database, non hardcoded in Java.
CREATE TABLE parcella (
    id                       BIGSERIAL PRIMARY KEY,
    nome                     VARCHAR(64) NOT NULL UNIQUE,               -- es. "parcellaA"
    varieta                  VARCHAR(64) NOT NULL,                       -- es. "Sangiovese"
    colore_bacca             VARCHAR(16) NOT NULL
                                 CHECK (colore_bacca IN ('nero', 'bianco')),
    lunghezza_germoglio_cm   DOUBLE PRECISION,                           -- dato fenologico da sopralluogo manuale (regola "tre dieci")
    germoglio_aggiornato_il  DATE,                                       -- data dell'ultimo sopralluogo che ha aggiornato il valore sopra
    latitudine               DOUBLE PRECISION,
    longitudine              DOUBLE PRECISION,
    note                     TEXT
);

-- =====================================================================
-- Rete di sensori
-- =====================================================================

CREATE TABLE nodo_sensore (
    id                  BIGSERIAL PRIMARY KEY,
    codice              VARCHAR(64) NOT NULL UNIQUE,       -- es. "meteo-A1"
    parcella_id         BIGINT NOT NULL REFERENCES parcella(id),
    -- Il CHECK rende esplicito e verificabile a livello di database
    -- l'insieme dei quattro tipi di nodo effettivamente usati dal sistema.
    tipo_nodo           VARCHAR(16) NOT NULL
                            CHECK (tipo_nodo IN ('meteo', 'idrico', 'bacca', 'suolo')),
    attivo              BOOLEAN NOT NULL DEFAULT TRUE,
    data_installazione  DATE NOT NULL DEFAULT CURRENT_DATE
);

-- =====================================================================
-- Catalogo delle regole di rischio e delle relative soglie bibliografiche
-- =====================================================================

-- Le regole del decision engine hanno soglie con fonte bibliografica esplicita: dal database
-- non era in alcun modo consultabile o verificabile.
-- Qui "regola_codice" diventa una vera FK verso un catalogo di regole, eliminando la
-- ridondanza e rendendo possibile risalire, a partire da un'allerta, alla soglia esatta
-- e alla fonte bibliografica che l'hanno generata.
CREATE TABLE regola (
    codice               VARCHAR(64) PRIMARY KEY,   -- "stress_idrico" | "ondata_di_calore" | "tre_dieci" | "sunburn" | ...
    descrizione          TEXT NOT NULL,
    fonte_bibliografica  VARCHAR(255) NOT NULL
);

-- Una riga per ogni singola condizione soglia della regola: modella naturalmente sia le
-- regole a soglia singola (ondata di calore) sia quelle a più condizioni (tre dieci: tre
-- condizioni simultanee; sunburn: quattro coppie soglia/durata LT50 da Schmidt et al. 2023).
CREATE TABLE regola_soglia (
    id                      BIGSERIAL PRIMARY KEY,
    regola_codice           VARCHAR(64) NOT NULL REFERENCES regola(codice),
    -- temperatura_suolo aggiunto per svernamento_oospore e danno_radicale (due scopi
    -- diversi sullo stesso sensore). Restano fuori velocita_vento/umidita_suolo 
    -- (pubblicati da sensors-simulator, v. misurazione.parametro): nessuna regola 
    -- li usa come soglia diretta oggi — umidita_suolo è un indicatore complementare 
    -- tracciato a runtime da RegolaStressIdrico, non una condizione scritta qui.
    parametro                VARCHAR(32) NOT NULL
                                 CHECK (parametro IN ('temperatura_aria', 'umidita_aria', 'pioggia',
                                                       'bagnatura_fogliare', 'psi_stem', 'temperatura_bacca',
                                                       'germogli', 'temperatura_suolo')),
    livello_rischio          VARCHAR(16) NOT NULL
                                 CHECK (livello_rischio IN ('moderato', 'severo')),
    operatore                 VARCHAR(2) NOT NULL
                                 CHECK (operatore IN ('<', '<=', '>', '>=')),
    valore_soglia             DOUBLE PRECISION NOT NULL,
    unita_misura              VARCHAR(16) NOT NULL,
    durata_minima_minuti      INTEGER,                -- valorizzato solo per soglie con condizione di durata (finestra pioggia, LT50 sunburn, bagnatura infezione_secondaria)
    note                      TEXT
);

-- =====================================================================
-- Tabella di incubazione di Goidanich (peronospora)
-- =====================================================================

-- Percentuale giornaliera di sviluppo dell'incubazione della peronospora, in
-- funzione di temperatura media (14-26°C, gradi interi, nessuna interpolazione
-- fra righe) e livello di umidità relativa (soglia >=90% = "alta"), secondo la
-- tabella di Goidanich (1964) riportata in Pertot, I. et al. (2007).
CREATE TABLE soglia_incubazione_goidanich (
    temperatura_media                  INTEGER NOT NULL
                                           CHECK (temperatura_media BETWEEN 14 AND 26),
    umidita_alta                       BOOLEAN NOT NULL,
    percentuale_incremento_giornaliero DOUBLE PRECISION NOT NULL
                                           CHECK (percentuale_incremento_giornaliero > 0),
    PRIMARY KEY (temperatura_media, umidita_alta)
);

-- =====================================================================
-- Catalogo delle azioni di mitigazione
-- =====================================================================

CREATE TABLE azione_mitigazione (
    codice               VARCHAR(32) PRIMARY KEY,
    descrizione          TEXT NOT NULL,
    fonte_bibliografica  VARCHAR(255)
);

-- Quali azioni sono documentate come applicabili a quale regola, con eventuale
-- nota. Una regola può avere più azioni possibili (es. sunburn); MappatoreAzione
-- oggi ne implementa comunque una sola per regola. Questa tabella documenta lo 
-- spazio di scelta disponibile in letteratura, non lo pilota a runtime.
CREATE TABLE regola_azione (
    id             BIGSERIAL PRIMARY KEY,
    regola_codice  VARCHAR(64) NOT NULL REFERENCES regola(codice),
    azione_codice  VARCHAR(32) NOT NULL REFERENCES azione_mitigazione(codice),
    note           TEXT
);

-- =====================================================================
-- Misurazioni e allerte
-- =====================================================================

CREATE TABLE misurazione (
    id              BIGSERIAL PRIMARY KEY,
    nodo_id         BIGINT NOT NULL REFERENCES nodo_sensore(id),
    -- CHECK sui nove parametri con un impiego reale nella logica applicativa, pubblicati
    -- da sensors-simulator/simulator/generator.py: i sei originari più velocita_vento
    -- (Smart & Sinclair 1976, già lavorato nel simulatore nella generazione di temperatura_bacca),
    -- temperatura_suolo e umidita_suolo (nodo suolo).
    -- radiazione_solare, pubblicato dallo stesso nodo meteo, è stato rimosso da questo
    -- CHECK: nessuna soglia istantanea utilizzabile è stata reperita in letteratura
    -- peer-reviewed per Vitis vinifera (l'unica fonte trovata è una soglia cumulata
    -- su base stagionale, non operazionalizzabile su una singola misurazione) — un dato
    -- senza un riferimento bibliografico preciso per un uso reale non trova posto qui.
    parametro       VARCHAR(32) NOT NULL
                        CHECK (parametro IN ('temperatura_aria', 'umidita_aria', 'pioggia',
                                              'bagnatura_fogliare', 'psi_stem', 'temperatura_bacca',
                                              'velocita_vento',
                                              'temperatura_suolo', 'umidita_suolo')),
    valore          DOUBLE PRECISION NOT NULL,
    unita_misura    VARCHAR(16) NOT NULL,
    rilevato_il     TIMESTAMPTZ NOT NULL,
    ricevuto_il     TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_misurazione_nodo_tempo ON misurazione (nodo_id, rilevato_il DESC);
CREATE INDEX idx_misurazione_parametro ON misurazione (parametro, rilevato_il DESC);

CREATE TABLE allerta (
    id                  BIGSERIAL PRIMARY KEY,
    tipo                VARCHAR(32) NOT NULL,
    livello_rischio     VARCHAR(16) NOT NULL
                            CHECK (livello_rischio IN ('moderato', 'severo')),
    nodo_id             BIGINT REFERENCES nodo_sensore(id),
    -- Questa colonna dà alla regola "tre dieci" (e a qualunque futura regola a livello di parcella) un
    -- riferimento esplicito, senza dover passare da un nodo specifico.
    parcella_id         BIGINT REFERENCES parcella(id),
    descrizione         TEXT NOT NULL,
    regola_codice       VARCHAR(64) NOT NULL REFERENCES regola(codice),
    generata_il         TIMESTAMPTZ NOT NULL DEFAULT now(),
    -- Questa colonna, nullable, è oggi effettivamente scritta e riletta all'avvio da SchedulerRisoluzioneAllerte
    -- (modulo persistence): permette allo scheduler di ricostruire all'avvio le
    -- pianificazioni pendenti leggendo dal database invece che perderle. Query
    -- attesa "SELECT id, risoluzione_pianificata_il FROM allerta WHERE stato =
    -- 'attiva' AND risoluzione_pianificata_il IS NOT NULL".
    risoluzione_pianificata_il TIMESTAMPTZ,
    risolta_il          TIMESTAMPTZ,
    stato               VARCHAR(16) NOT NULL DEFAULT 'attiva'
                            CHECK (stato IN ('attiva', 'risolta'))
);

CREATE INDEX idx_allerta_stato ON allerta (stato, generata_il DESC);
-- tipo e parcella_id sono entrambi filtri diretti in AllertaSpecifications (api). Tre run
-- di test di carico non hanno mai misurato un sovrapprezzo ai volumi testati: questi due
-- indici sono una precauzione per sessioni sperimentali future molto più lunghe, in
-- assenza di una logica di pulizia periodica del database — non la correzione di un
-- problema osservato.
CREATE INDEX idx_allerta_tipo ON allerta (tipo);
CREATE INDEX idx_allerta_parcella ON allerta (parcella_id);
CREATE TABLE trattamento (
    id              BIGSERIAL PRIMARY KEY,
    allerta_id      BIGINT NOT NULL REFERENCES allerta(id),
    tipo_azione     VARCHAR(32) NOT NULL REFERENCES azione_mitigazione(codice),
    eseguito_il     TIMESTAMPTZ NOT NULL DEFAULT now(),
    esito           VARCHAR(16) NOT NULL DEFAULT 'simulato',
    note            TEXT
);

-- =====================================================================
-- Seed: le tre parcelle reali del vigneto simulato (sensors-simulator/config/nodi.yaml)
-- =====================================================================

INSERT INTO parcella (nome, varieta, colore_bacca, lunghezza_germoglio_cm, germoglio_aggiornato_il, latitudine, longitudine) VALUES
    ('parcellaA', 'Sangiovese',    'nero',   12, CURRENT_DATE, 41.1231, 16.8674),
    ('parcellaB', 'Montepulciano', 'nero',   14, CURRENT_DATE, 41.1258, 16.8711),
    ('parcellaC', 'Trebbiano',     'bianco', 10, CURRENT_DATE, 41.1204, 16.8639);

-- =====================================================================
-- Seed: tabella di incubazione di Goidanich
-- =====================================================================

-- Valori identici, riga per riga, a TABELLA in TabellaGoidanich.java (modulo backend/decisionengine).
INSERT INTO soglia_incubazione_goidanich (temperatura_media, umidita_alta, percentuale_incremento_giornaliero) VALUES
    (14, FALSE, 6.6),  (14, TRUE, 9.0),
    (15, FALSE, 7.6),  (15, TRUE, 10.5),
    (16, FALSE, 8.6),  (16, TRUE, 11.7),
    (17, FALSE, 10.0), (17, TRUE, 13.3),
    (18, FALSE, 11.1), (18, TRUE, 13.3),
    (19, FALSE, 12.5), (19, TRUE, 16.6),
    (20, FALSE, 14.2), (20, TRUE, 20.0),
    (21, FALSE, 15.3), (21, TRUE, 22.2),
    (22, FALSE, 16.6), (22, TRUE, 22.2),
    (23, FALSE, 18.1), (23, TRUE, 25.0),
    (24, FALSE, 18.1), (24, TRUE, 25.0),
    (25, FALSE, 16.6), (25, TRUE, 22.2),
    (26, FALSE, 16.6), (26, TRUE, 22.2);

-- ==========================================================================
-- Seed: catalogo regole e soglie, ciascuna con fonte bibliografica esplicita
-- ==========================================================================

INSERT INTO regola (codice, descrizione, fonte_bibliografica) VALUES
    ('stress_idrico',
        'Stress idrico della vite in base al potenziale idrico dello stelo (Ψstem)',
        'Acevedo-Opazo et al., 2010'),
    ('ondata_di_calore',
        'Ondata di calore in base alla temperatura dell''aria',
        'Valentini et al., 2024; Tarricone et al., 2020'),
    ('tre_dieci',
        'Regola dei "tre dieci" per il rischio di infezione primaria da peronospora',
        'Baldacci, 1947; tabella di incubazione di Goidanich, 1957/1964'),
    ('sunburn',
        'Scottatura da esposizione solare della bacca (logica intensità×durata)',
        'Gambetta et al., 2021; Schmidt et al., 2023'),
    ('svernamento_oospore',
        'Temperatura del suolo favorevole alla germinazione delle oospore svernanti di Plasmopara viticola',
        'Si Ammour et al., 2020'),
    ('infezione_secondaria',
        'Rischio di infezione secondaria di peronospora per bagnatura fogliare prolungata a temperatura favorevole',
        'Brischetto et al., 2021'),
    ('danno_radicale',
        'Temperatura del suolo oltre la soglia fisiologica di danno alla sopravvivenza radicale',
        'Field et al., 2020');

INSERT INTO regola_soglia (regola_codice, parametro, livello_rischio, operatore, valore_soglia, unita_misura, durata_minima_minuti, note) VALUES
    -- Stress idrico: due soglie, isteresi di 0.05 MPa applicata a runtime (non rappresentata qui)
    ('stress_idrico', 'psi_stem', 'moderato', '<', -1.2, 'MPa', NULL, 'Isteresi di uscita 0.05 MPa applicata a runtime'),
    ('stress_idrico', 'psi_stem', 'severo',   '<', -1.4, 'MPa', NULL, 'Isteresi di uscita 0.05 MPa applicata a runtime'),

    -- Ondata di calore: soglia moderata da temperatura dell'aria; soglia severa (40°C) da
    -- temperatura fogliare su V. amurensis, non V. vinifera — applicata per convenzione
    ('ondata_di_calore', 'temperatura_aria', 'moderato', '>', 35.0, 'C', NULL, 'Isteresi di uscita 1°C applicata a runtime'),
    ('ondata_di_calore', 'temperatura_aria', 'severo', '>=', 40.0, 'C', NULL, 'Convenzionale, da tessuto fogliare su V. amurensis, Luo et al. 2011'),

    -- Tre dieci: tre condizioni simultanee, unico livello di rischio (moderato)
    ('tre_dieci', 'temperatura_aria', 'moderato', '>=', 10.0, 'C', NULL, NULL),
    ('tre_dieci', 'pioggia', 'moderato', '>=', 10.0, 'mm', 2880, 'Finestra di 48h: estremo più ampio dell''intervallo bibliografico 24-48h, scelta metodologica'),
    ('tre_dieci', 'germogli', 'moderato', '>=', 10.0, 'cm', NULL, 'Dato fenologico manuale, non da sensore: v. parcella.lunghezza_germoglio_cm'),

    -- Sunburn: soglia di ingresso + quattro coppie soglia/durata di dose letale (LT50)
    ('sunburn', 'temperatura_bacca', 'moderato', '>=', 45.00, 'C', NULL, 'Isteresi di uscita 1°C; range di rischio 45-49°C'),
    ('sunburn', 'temperatura_bacca', 'severo',   '>=', 53.79, 'C', 15, 'Dose letale (LT50), Schmidt et al. 2023'),
    ('sunburn', 'temperatura_bacca', 'severo',   '>=', 49.94, 'C', 30, 'Dose letale (LT50), Schmidt et al. 2023'),
    ('sunburn', 'temperatura_bacca', 'severo',   '>=', 47.82, 'C', 60, 'Dose letale (LT50), Schmidt et al. 2023'),
    ('sunburn', 'temperatura_bacca', 'severo',   '>=', 47.06, 'C', 90, 'Dose letale (LT50), Schmidt et al. 2023'),
    
    -- Svernamento oospore: due soglie (minima e massima), unico livello di rischio
    ('svernamento_oospore', 'temperatura_suolo', 'moderato', '>=', 12.0, 'C', NULL, 'Soglia minima di germinazione, Si Ammour et al. 2020'),
    ('svernamento_oospore', 'temperatura_suolo', 'moderato', '<=', 32.0, 'C', NULL, 'Soglia di inibizione, Si Ammour et al. 2020'),

    -- Infezione secondaria: tre condizioni simultanee, unico livello di rischio.
    -- Semplificazione dichiarata: il modello di Brischetto definisce "ora umida" come
    -- umidita_aria >=80% OPPURE pioggia >0mm OPPURE bagnatura_fogliare >30min (un OR fra tre
    -- segnali diversi); qui si usa il solo bagnatura_fogliare come segnale di bagnatura,
    -- rinunciando alla componente OR con umidità/pioggia. La soglia del 40% è una convenzione
    -- di questo progetto (non da Brischetto direttamente), scelta per intercettare le notti
    -- di rugiada tipiche generate dal simulatore oltre alla pioggia — sullo stesso piano
    -- dichiarativo della soglia convenzionale di umidità 90% già usata per Goidanich.
    ('infezione_secondaria', 'bagnatura_fogliare', 'moderato', '>=', 40.0, '%', 120, 'Soglia di bagnatura convenzionale (non da Brischetto); durata minima 2h all''ottimo, Brischetto et al. 2021'),
    ('infezione_secondaria', 'temperatura_aria', 'moderato', '>=', 4.0, 'C', NULL, 'Temperatura minima per l''infezione, Brischetto et al. 2021'),
    ('infezione_secondaria', 'temperatura_aria', 'moderato', '<=', 30.2, 'C', NULL, 'Temperatura massima per l''infezione, Brischetto et al. 2021'),
    -- Danno radicale: soglia fisiologica, distinta da svernamento_oospore (fitosanitaria) —
    -- stesso parametro temperatura_suolo usato per due scopi diversi
    ('danno_radicale', 'temperatura_suolo', 'severo', '>=', 35.0, 'C', NULL, 'Soglia di danno radicale, Field et al. 2020');

-- =====================================================================
-- Seed: catalogo delle azioni di mitigazione e relative applicabilità per regola
-- =====================================================================

INSERT INTO azione_mitigazione (codice, descrizione, fonte_bibliografica) VALUES
    ('irrigazione_soccorso', 'Irrigazione di soccorso in risposta a stress idrico', NULL),
    ('nebulizzazione', 'Raffrescamento per nebulizzazione della chioma/bacca', NULL),
    ('trattamento_fitosanitario', 'Trattamento fitosanitario mirato (peronospora)', NULL),
    ('applicazione_caolino', 'Film di particelle di caolino sulla bacca, effetto schermante/riflettente',
        'Studi 2016-2024 su caolino in viticoltura'),
    ('rete_ombreggiante', 'Rete ombreggiante al 30-70% sulla fascia produttiva',
        'Studi 2016-2024 su mitigazione sunburn in viticoltura'),
    ('applicazione_zeolite', 'Applicazione di zeoliti in combinazione con irrigazione in fase di maturazione',
        'Studio Univ. Bologna 2024, cv. Sangiovese');

INSERT INTO regola_azione (regola_codice, azione_codice, note) VALUES
    ('stress_idrico', 'irrigazione_soccorso', NULL),
    ('tre_dieci', 'trattamento_fitosanitario', NULL),
    ('ondata_di_calore', 'nebulizzazione',
        'Sistema di nebulizzazione automatico attivato a 35°C su Sangiovese/Montepulciano, Valentini et al. 2024'),
    ('sunburn', 'nebulizzazione', 'Unica azione oggi effettivamente scelta da MappatoreAzione per questa regola'),
    ('sunburn', 'applicazione_caolino', 'Bacche fino a 6-7,6°C più fredde del controllo non trattato'),
    ('sunburn', 'rete_ombreggiante', 'Studiata in combinazione con il caolino negli stessi due studi'),
    ('sunburn', 'applicazione_zeolite', 'Riduce necrosi e shrivel se combinata con irrigazione in fase di maturazione');