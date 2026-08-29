// Load test k6 per la REST API di GrapeHealth.
//
// Uso:
//   k6 run tests/load/grapehealth-load-test.js
//   k6 run --out json=risultati.json tests/load/grapehealth-load-test.js
//   (unit test delle funzioni pure: cd tests/load && npm test)

import http from 'k6/http';
import { check, sleep } from 'k6';
import { Trend } from 'k6/metrics';
import { randItem, randSample } from './helpers.js';

const BASE_URL = __ENV.BASE_URL || 'https://localhost:8084';

// Trend separati per endpoint: la media aggregata di k6 (http_req_duration)
// mescola endpoint dal costo molto diverso tra loro. Allerte non filtrate
// separate da allerte filtrate per tipo/parcella (query non indicizzata),
// raccomandazione singola separata da raccomandazione batch;
// /api/parcelle ha il proprio Trend.
const durataMisurazioni = new Trend('durata_misurazioni', true);
const durataAllerte = new Trend('durata_allerte', true);
const durataAllerteFiltrate = new Trend('durata_allerte_filtrate', true);
const durataRaccomandazioni = new Trend('durata_raccomandazioni', true);
const durataRaccomandazioniBatch = new Trend('durata_raccomandazioni_batch', true);
const durataParcelle = new Trend('durata_parcelle', true);

const PARCELLE = ['parcellaA', 'parcellaB', 'parcellaC'];
// Le stesse tre parcelle del seed, più un nome deliberatamente inesistente:
// usato per esercitare anche il percorso 404 di /api/parcelle/{nome} (GlobalExceptionHandler).
const NOMI_PARCELLA_CON_INESISTENTE = [...PARCELLE, 'parcellaInesistente'];
// Le stesse tre parcelle, più un nome inesistente: usata solo per il filtro
// di /api/allerte, dove AllerteService.cerca() restituisce 200 con pagina
// vuota per una parcella non riconosciuta — un ramo distinto da "tipo e
// parcella validi ma senza corrispondenze in questa sessione", mai
// esercitato finora perché la chiamata #3 pescava sempre da PARCELLE.
const PARCELLE_ALLERTA_CON_INESISTENTE = [...PARCELLE, 'parcellaInesistente'];
// Le stesse tre parcelle, più un nome inesistente: usata per il filtro di
// /api/misurazioni, dove StoricoMisurazioniService.cerca() restituisce 200
// con pagina vuota per una parcella non riconosciuta (NodoResolver
// restituisce una lista di nodi vuota, non null) — lo stesso comportamento
// già verificato su /api/allerte, qui mai esercitato perché la chiamata #1
// pescava sempre da PARCELLE.
const PARCELLE_MISURAZIONI_CON_INESISTENTE = [...PARCELLE, 'parcellaInesistente'];
const durataNodi = new Trend('durata_nodi', true);
// I dodici nodi realmente censiti (quattro tipi per ciascuna delle tre
// parcelle), più un codice deliberatamente inesistente per il percorso 404.
const NODI_CON_INESISTENTE = [
  'meteo-A1', 'idrico-A1', 'bacca-A1', 'suolo-A1',
  'meteo-B1', 'idrico-B1', 'bacca-B1', 'suolo-B1',
  'meteo-C1', 'idrico-C1', 'bacca-C1', 'suolo-C1',
  'nodoInesistente',
];
const durataMisurazioniIntervallo = new Trend('durata_misurazioni_intervallo', true);
// I nove parametri pubblicati dai quattro tipi di nodo:
// meteo -> temperatura_aria, umidita_aria, pioggia, bagnatura_fogliare, velocita_vento;
// idrico -> psi_stem; bacca -> temperatura_bacca; suolo -> temperatura_suolo, umidita_suolo.
const PARAMETRI = ['temperatura_aria', 'umidita_aria', 'pioggia', 'bagnatura_fogliare', 'psi_stem', 'temperatura_bacca', 'velocita_vento', 'temperatura_suolo', 'umidita_suolo'];
// I sette tipi di allerta prodotti dal decision engine.
const TIPI_ALLERTA = ['stress_idrico', 'ondata_di_calore', 'sunburn', 'tre_dieci', 'svernamento_oospore', 'infezione_secondaria', 'danno_radicale'];
// svernamento_oospore, infezione_secondaria e danno_radicale sono allerte di solo
// monitoraggio, senza un'azione di mitigazione catalogata: l'arricchimento
// bibliografico (fonte e descrizione della regola) resta presente, ma senza azioni
// alternative, e nessun trattamento viene simulato per loro.
const TIPI_SENZA_AZIONE = ['svernamento_oospore', 'infezione_secondaria', 'danno_radicale'];
// Dimensione del campione per la chiamata batch /api/raccomandazioni?allertaIds=...
const CAMPIONE_BATCH_RACCOMANDAZIONI = 5;

export const options = {
  // smoke verifica che script e sistema rispondano correttamente prima di
  // salire di carico; load sale gradualmente fino a 30 VU concorrenti,
  // resta a regime per 2 minuti, poi ridiscende a 0.
  scenarios: {
    smoke: {
      executor: 'constant-vus',
      vus: 3,
      duration: '30s',
      exec: 'default',
      startTime: '0s',
    },
    load: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: '30s', target: 10 },
        { duration: '30s', target: 30 },
        { duration: '2m', target: 30 },
        { duration: '30s', target: 0 },
      ],
      exec: 'default',
      startTime: '35s',
    },
  },
  // Soglie volutamente larghe rispetto ai tempi di risposta tipici (poche
  // decine di millisecondi): margine per un sistema locale che gira con
  // RabbitMQ, Postgres e cinque istanze Tomcat sulla stessa macchina.
  thresholds: {
    http_req_failed: ['rate<0.01'],
    http_req_duration: ['p(95)<1500'],
    durata_misurazioni: ['p(95)<2000'],
    durata_allerte: ['p(95)<1000'],
    durata_allerte_filtrate: ['p(95)<1000'],
    durata_raccomandazioni: ['p(95)<1000'],
    durata_raccomandazioni_batch: ['p(95)<1500'],
    durata_parcelle: ['p(95)<1000'],
    durata_nodi: ['p(95)<1000'],
    durata_misurazioni_intervallo: ['p(95)<2000'],
  },
};

// Eseguita una sola volta prima di tutti gli scenari. Recupera fino a 100 id
// di allerte già risolte, usati da ogni iterazione per interrogare
// /api/raccomandazioni su allerte realmente esistenti, e verifica una volta
// sola il catalogo delle parcelle (dato statico, non serve ripeterlo a ogni
// iterazione).
export function setup() {
  const res = http.get(`${BASE_URL}/api/allerte?stato=risolta&size=100`);

  let idAllerteRisolte = [];
  try {
    const body = JSON.parse(res.body);
    idAllerteRisolte = (body.content || []).map((a) => a.id);
  } catch (e) {
    idAllerteRisolte = [];
  }

  if (idAllerteRisolte.length === 0) {
    console.warn(
      'setup(): nessuna allerta risolta trovata a DB. ' +
      '/api/raccomandazioni verra\' testato senza allertaId (lista delle attive, ' +
      'probabilmente vuota) e i check su questo endpoint falliranno per l\'intera ' +
      'durata del test: prima di rilanciare k6, fai girare il simulatore con uno ' +
      'scenario di stress (stress_idrico / ondata_di_calore) per qualche minuto.'
    );
  } else {
    console.log(`setup(): trovate ${idAllerteRisolte.length} allerte risolte, id campione: ${idAllerteRisolte.slice(0, 5).join(', ')}...`);
  }

  // /api/parcelle interrogato una sola volta, mirroring esatto del pattern reale della dashboard
  // (dashboard/js/main.js carica PARCELLE_INFO una volta al bootstrap, non a
  // ogni render).
  const resParcelle = http.get(`${BASE_URL}/api/parcelle`);
  check(resParcelle, {
    'setup: /api/parcelle status 200': (r) => r.status === 200,
    'setup: tutte le parcelle del seed hanno varieta/colore bacca/germoglio (valore e data aggiornamento)': (r) => {
      try {
        const lista = JSON.parse(r.body);
        return lista.length === PARCELLE.length && lista.every(
          (p) => Boolean(p.varieta) && Boolean(p.coloreBacca) && p.lunghezzaGermoglioCm != null && Boolean(p.germoglioAggiornatoIl)
        );
      } catch (e) {
        return false;
      }
    },
  });

  // /api/nodi interrogato una sola volta, stesso pattern di bootstrap-once di
  // /api/parcelle: dashboard/js/main.js lo carica al bootstrap per il KPI
  // "nodi operativi", perché lo stato di un nodo non cambia entro la durata
  // di una sessione.
  const resNodi = http.get(`${BASE_URL}/api/nodi`);
  check(resNodi, {
    'setup: /api/nodi status 200': (r) => r.status === 200,
    'setup: tutti i nodi del seed hanno codice/tipo/parcella/data installazione': (r) => {
      try {
        const lista = JSON.parse(r.body);
        return lista.length === 12 && lista.every(
          (n) => Boolean(n.codice) && Boolean(n.tipoNodo) && Boolean(n.parcella) && Boolean(n.dataInstallazione)
        );
      } catch (e) {
        return false;
      }
    },
  });

  return { idAllerteRisolte };
}

// Una iterazione = una sessione cliente che consulta in sequenza misurazioni,
// allerte (tre varianti), raccomandazioni (singola e in batch) e il
// catalogo delle parcelle.
export default function (data) {
  // --- GET /api/misurazioni ---
  // Un quinto delle chiamate omette del tutto 'parcella': la vista aggregata
  // multi-parcella che main.js usa realmente quando il filtro e' vuoto
  // (grafico multi-serie in charts.js).
  const omettiParcella = Math.random() < 0.2;
  const parcella = omettiParcella ? null : randItem(PARCELLE_MISURAZIONI_CON_INESISTENTE);
  // parametro omesso in una minoranza di casi: nessun consumatore reale lo fa
  // oggi, ma resta un comportamento dichiarato (MisurazioneSpecifications.parametro(null))
  // altrimenti mai raggiunto in questo script.
  const omettiParametro = Math.random() < 0.1;
  const parametro = omettiParametro ? null : randItem(PARAMETRI);
  const parcellaSconosciuta = !omettiParcella && parcella === 'parcellaInesistente';

  // Un caso su dieci usa un solo estremo dell'intervallo temporale (solo 'dal'
  // o solo 'al'): a differenza della chiamata su dal+al insieme (piu' sotto),
  // un solo estremo non fa scattare il ramo non paginato del service, resta
  // sul percorso normale con un predicato temporale singolo — comportamento
  // dichiarato, mai esercitato finora ne' qui ne' nella chiamata sull'intervallo.
  const usaEstremoSingolo = Math.random() < 0.1;
  const estremoSingolo = usaEstremoSingolo
    ? (Math.random() < 0.5
        ? `dal=${new Date(Date.now() - 3600000).toISOString()}`
        : `al=${new Date().toISOString()}`)
    : null;

  const partiMisurazioni = ['size=50'];
  if (!omettiParcella) partiMisurazioni.push(`parcella=${parcella}`);
  if (!omettiParametro) partiMisurazioni.push(`parametro=${parametro}`);
  if (estremoSingolo) partiMisurazioni.push(estremoSingolo);
  let res = http.get(`${BASE_URL}/api/misurazioni?${partiMisurazioni.join('&')}`, { tags: { endpoint: 'misurazioni' } });
  durataMisurazioni.add(res.timings.duration);
  check(res, {
    'misurazioni: status 200': (r) => r.status === 200,
    'misurazioni: corpo ben formato': (r) => {
      try {
        return Array.isArray(JSON.parse(r.body).content);
      } catch (e) {
        return false;
      }
    },
  });
  if (usaEstremoSingolo) {
    // Nessun'altra asserzione: come per l'intervallo dal+al completo piu'
    // sotto, un estremo ancorato all'orologio reale non garantisce di
    // trovare corrispondenze contro rilevatoIl, scritto sull'orologio
    // simulato - l'affidabilita' del contenuto in questo caso non e'
    // responsabilita' di questo endpoint.
  } else if (omettiParcella) {
    check(res, {
      'misurazioni (vista aggregata, parcella omessa): contenuto non vuoto': (r) => {
        try {
          return JSON.parse(r.body).content.length > 0;
        } catch (e) {
          return false;
        }
      },
      'misurazioni (vista aggregata): comprende piu\' di una parcella': (r) => {
        try {
          const parcelle = new Set(JSON.parse(r.body).content.map((m) => m.parcella));
          return parcelle.size > 1;
        } catch (e) {
          return false;
        }
      },
    });
  } else if (parcellaSconosciuta) {
    check(res, {
      'misurazioni (parcella sconosciuta): pagina vuota, non un errore': (r) => {
        try {
          return JSON.parse(r.body).content.length === 0;
        } catch (e) {
          return false;
        }
      },
    });
  } else {
    check(res, {
      'misurazioni: contenuto non vuoto': (r) => {
        try {
          return JSON.parse(r.body).content.length > 0;
        } catch (e) {
          return false;
        }
      },
      'misurazioni: arricchimento nodo/parcella presente': (r) => {
        try {
          const primo = JSON.parse(r.body).content[0];
          return Boolean(primo) && Boolean(primo.nodoCodice) && Boolean(primo.parcella);
        } catch (e) {
          return false;
        }
      },
    });
  }

  sleep(0.3);

  // --- GET /api/allerte (stato=risolta) ---
  res = http.get(`${BASE_URL}/api/allerte?stato=risolta&size=50`, {
    tags: { endpoint: 'allerte' },
  });
  durataAllerte.add(res.timings.duration);
  check(res, {
    'allerte: status 200': (r) => r.status === 200,
    'allerte: contenuto non vuoto': (r) => {
      try {
        return JSON.parse(r.body).content.length > 0;
      } catch (e) {
        return false;
      }
    },
    'allerte: regolaScatenante presente': (r) => {
      try {
        const primo = JSON.parse(r.body).content[0];
        return Boolean(primo) && Boolean(primo.regolaScatenante);
      } catch (e) {
        return false;
      }
    },
    'allerte: metadati di paginazione coerenti': (r) => {
      try {
        const body = JSON.parse(r.body);
        return body.page
          && typeof body.page.totalElements === 'number'
          && typeof body.page.totalPages === 'number'
          && body.page.totalElements >= body.content.length;
      } catch (e) {
        return false;
      }
    },
  });

  // --- GET /api/allerte (stato=risolta, filtrata per tipo e/o parcella) ---
  // alerts.js:97 imposta i due filtri in modo indipendente (ciascuno puo'
  // essere omesso singolarmente): replicato qui, non piu' sempre insieme.
  const usaTipo = Math.random() < 0.75;
  const usaParcellaFiltro = Math.random() < 0.75;
  const tipoAllerta = usaTipo ? randItem(TIPI_ALLERTA) : null;
  const parcellaAllerta = usaParcellaFiltro ? randItem(PARCELLE_ALLERTA_CON_INESISTENTE) : null;
  const partiAllerteFiltrate = ['stato=risolta', 'size=50'];
  if (tipoAllerta) partiAllerteFiltrate.push(`tipo=${tipoAllerta}`);
  if (parcellaAllerta) partiAllerteFiltrate.push(`parcella=${parcellaAllerta}`);
  res = http.get(`${BASE_URL}/api/allerte?${partiAllerteFiltrate.join('&')}`, {
    tags: { endpoint: 'allerte_filtrate' },
  });
  durataAllerteFiltrate.add(res.timings.duration);
  check(res, {
    'allerte (filtrate per tipo e/o parcella): status 200': (r) => r.status === 200,
  });

  // --- GET /api/allerte (stato=attiva, esplicito o omesso) ---
  // AllerteService.cerca() applica "attiva" come default quando stato è
  // omesso (verificato nel codice, non solo dichiarato nello Swagger): un
  // caso su due qui omette il parametro invece di specificarlo, per
  // esercitare anche quel ramo. Il check sul contenuto è sicuro anche a
  // lista vuota: "ogni elemento ha stato attiva" è vero per costruzione
  // quando gli elementi sono zero.
  const omettiStato = Math.random() < 0.5;
  const urlAllerteAttive = omettiStato
    ? `${BASE_URL}/api/allerte?size=50`
    : `${BASE_URL}/api/allerte?stato=attiva&size=50`;
  res = http.get(urlAllerteAttive, { tags: { endpoint: 'allerte' } });
  durataAllerte.add(res.timings.duration);
  check(res, {
    'allerte (attive, esplicito o omesso): status 200': (r) => r.status === 200,
    'allerte (attive, esplicito o omesso): contenuto coerente con "attiva"': (r) => {
      try {
        return JSON.parse(r.body).content.every((a) => a.stato === 'attiva');
      } catch (e) {
        return false;
      }
    },
  });

  sleep(0.3);

  // --- GET /api/raccomandazioni (allertaId singolo, o nessun parametro) ---
  // Senza allertaId né allertaIds, RaccomandazioneController usa
  // perAllerteAttive() — il ramo che dashboard/js/stats.js chiama davvero
  // per il grafico delle allerte attive. Esercitato qui un caso su cinque,
  // deliberatamente, non solo come fallback teorico di un pool vuoto (che in
  // pratica non si è mai verificato): senza check di contenuto in quel caso,
  // perché il numero di allerte attive in un dato istante può legittimamente
  // essere zero.
  const ids = data.idAllerteRisolte;
  const usaNessunParametro = ids.length === 0 || Math.random() < 0.2;
  const url = usaNessunParametro
    ? `${BASE_URL}/api/raccomandazioni`
    : `${BASE_URL}/api/raccomandazioni?allertaId=${randItem(ids)}`;

  res = http.get(url, { tags: { endpoint: 'raccomandazioni' } });
  durataRaccomandazioni.add(res.timings.duration);
  check(res, {
    'raccomandazioni: status 200': (r) => r.status === 200,
  });
  if (!usaNessunParametro) {
    check(res, {
      'raccomandazioni: contenuto non vuoto': (r) => {
        try {
          return JSON.parse(r.body).length > 0;
        } catch (e) {
          return false;
        }
      },
      'raccomandazioni: arricchimento bibliografico coerente con il tipo di allerta': (r) => {
        try {
          const prima = JSON.parse(r.body)[0];
          if (!prima) return false;
          if (!Boolean(prima.fonteBibliograficaRegola) || !Array.isArray(prima.azioniAlternative)) {
            return false;
          }
          // Le allerte di solo monitoraggio hanno fonte e descrizione della
          // regola, ma nessuna azione alternativa catalogata: lista vuota per
          // costruzione, non un errore.
          return TIPI_SENZA_AZIONE.includes(prima.tipoAllerta)
            ? prima.azioniAlternative.length === 0
            : prima.azioniAlternative.length > 0;
        } catch (e) {
          return false;
        }
      },
      'raccomandazioni: esito simulato coerente con l\'azione prevista': (r) => {
        try {
          const prima = JSON.parse(r.body)[0];
          if (!prima) return false;
          if (TIPI_SENZA_AZIONE.includes(prima.tipoAllerta)) {
            return prima.basedOnSimulatedExecution === false;
          }
          return prima.basedOnSimulatedExecution === true &&
            Boolean(prima.azioneEseguita) && Boolean(prima.esitoSimulato) &&
            Boolean(prima.eseguitaIl);
        } catch (e) {
          return false;
        }
      },
    });
  }

  sleep(0.3);

  // --- GET /api/raccomandazioni (allertaIds batch): stesso arricchimento
  // della chiamata singola, ma su più allerte in un'unica richiesta.
  const campioneBatch = randSample(ids, CAMPIONE_BATCH_RACCOMANDAZIONI);
  if (campioneBatch.length > 0) {
    const queryBatch = campioneBatch.map((id) => `allertaIds=${id}`).join('&');
    res = http.get(`${BASE_URL}/api/raccomandazioni?${queryBatch}`, {
      tags: { endpoint: 'raccomandazioni_batch' },
    });
    durataRaccomandazioniBatch.add(res.timings.duration);
    check(res, {
      'raccomandazioni (batch): status 200': (r) => r.status === 200,
      'raccomandazioni (batch): numero elementi coerente con l\'input': (r) => {
        try {
          return JSON.parse(r.body).length === campioneBatch.length;
        } catch (e) {
          return false;
        }
      },
    });
  }

  // --- GET /api/parcelle/{nome}.
  // Un nome su quattro è deliberatamente inesistente ('parcellaInesistente'):
  // testa sia il percorso 200 (dato reale, consumato da alerts.js) sia il
  // percorso 404 di GlobalExceptionHandler.
  // expectedStatuses(200, 404) evita che i 404 voluti sporchino la soglia globale http_req_failed.
  const nomeParcella = randItem(NOMI_PARCELLA_CON_INESISTENTE);
  res = http.get(`${BASE_URL}/api/parcelle/${nomeParcella}`, {
    tags: { endpoint: 'parcelle' },
    responseCallback: http.expectedStatuses(200, 404),
  });
  durataParcelle.add(res.timings.duration);
  check(res, {
    'parcelle: status 200 o 404 coerente con il nome richiesto': (r) => {
      const esisteDavvero = PARCELLE.includes(nomeParcella);
      return esisteDavvero ? r.status === 200 : r.status === 404;
    },
    'parcelle: corpo coerente con lo status': (r) => {
      try {
        const body = JSON.parse(r.body);
        if (r.status === 200) {
          return body.nome === nomeParcella && Boolean(body.varieta) && Boolean(body.coloreBacca);
        }
        return r.status === 404 && body.status === 404 && Boolean(body.messaggio);
      } catch (e) {
        return false;
      }
    },
  });

  // --- GET /api/nodi/{codice}: stesso schema 200/404 di /api/parcelle/{nome}.
  const codiceNodo = randItem(NODI_CON_INESISTENTE);
  res = http.get(`${BASE_URL}/api/nodi/${codiceNodo}`, {
    tags: { endpoint: 'nodi' },
    responseCallback: http.expectedStatuses(200, 404),
  });
  durataNodi.add(res.timings.duration);
  check(res, {
    'nodi: status 200 o 404 coerente con il codice richiesto': (r) => {
      const esisteDavvero = codiceNodo !== 'nodoInesistente';
      return esisteDavvero ? r.status === 200 : r.status === 404;
    },
    'nodi: corpo coerente con lo status': (r) => {
      try {
        const body = JSON.parse(r.body);
        if (r.status === 200) {
          return body.codice === codiceNodo && Boolean(body.tipoNodo) && Boolean(body.parcella);
        }
        return r.status === 404 && body.status === 404 && Boolean(body.messaggio);
      } catch (e) {
        return false;
      }
    },
  });

  // --- GET /api/misurazioni (dal+al insieme): ramo separato in
  // StoricoMisurazioniService — con entrambi i limiti presenti abbandona la
  // paginazione e restituisce l'intera finestra, per non troncare
  // silenziosamente un intervallo richiesto per intero. Un caso su due usa
  // un intervallo mal ordinato (dal dopo al), deliberatamente invalido:
  // ParametriNonValidiException -> 400. Nessuna assunzione sul contenuto
  // quando l'intervallo è valido: l'affidabilità dei dati in quella finestra
  // dipende dal disallineamento tra orologio reale e simulato, non da questo
  // endpoint.
  const oraReale = Date.now();
  const alValido = new Date(oraReale).toISOString();
  const dalValido = new Date(oraReale - 3600000).toISOString();
  const usaIntervalloInvalido = Math.random() < 0.5;
  const dal = usaIntervalloInvalido ? alValido : dalValido;
  const al = usaIntervalloInvalido ? dalValido : alValido;
  // Un quarto delle chiamate con intervallo valido omette 'parcella': stesso
  // caso reale di main.js con finestra attiva e filtro parcella vuoto, che
  // attiva il ramo piu' oneroso del controller (intera finestra, senza
  // paginazione, su tutti e dodici i nodi) — mai esercitato prima d'ora.
  const vistaAggregataIntervallo = !usaIntervalloInvalido && Math.random() < 0.25;
  // parametro presente nella grande maggioranza dei casi, coerente con l'uso
  // reale di main.js; omesso in una minoranza per non perdere copertura sul
  // ramo del predicato "nessun filtro parametro".
  const parametroIntervallo = Math.random() < 0.8 ? randItem(PARAMETRI) : null;
  const partiIntervallo = [`dal=${dal}`, `al=${al}`, 'size=50'];
  if (!vistaAggregataIntervallo) partiIntervallo.push(`parcella=${randItem(PARCELLE)}`);
  if (parametroIntervallo) partiIntervallo.push(`parametro=${parametroIntervallo}`);
  const urlIntervallo = `${BASE_URL}/api/misurazioni?${partiIntervallo.join('&')}`;
  res = http.get(urlIntervallo, {
    tags: { endpoint: 'misurazioni_intervallo' },
    responseCallback: http.expectedStatuses(200, 400),
  });
  durataMisurazioniIntervallo.add(res.timings.duration);
  check(res, {
    'misurazioni (intervallo dal/al): status coerente con l\'ordine delle date': (r) =>
      usaIntervalloInvalido ? r.status === 400 : r.status === 200,
    'misurazioni (intervallo dal/al): corpo coerente con lo status': (r) => {
      try {
        const body = JSON.parse(r.body);
        if (r.status === 400) {
          return body.status === 400 && Boolean(body.messaggio);
        }
        return Array.isArray(body.content);
      } catch (e) {
        return false;
      }
    },
  });

  sleep(0.5);
}