// KPI e grafici aggregati del pannello "Statistiche generali": conteggio
// allerte attive per parcella/livello, azioni raccomandate, tasso di
// esecuzione delle raccomandazioni, tempo medio esecuzione -> risoluzione, e
// ricorrenza di tipo/parcella tra le allerte risolte di recente. Una sola
// funzione pura in questo file, vociPiuFrequenti (v. stats.test.js): il resto
// resta intrecciato con una chiamata fetch o con l'aggiornamento diretto del
// DOM (v. anche api.test.js e charts.test.js per le altre funzioni pure della
// dashboard).
async function aggiornaStatistiche(allerteAttive) {
  document.getElementById('kpiAllerteAttive').textContent = allerteAttive.length;
  aggiornaKpiParcelleESeverita(allerteAttive);
  renderAlertParcellaChart('chartAllerteParcella', allerteAttive);
  renderAlertLivelloChart('chartAllerteLivello', allerteAttive);

  // Solo per il grafico "Azioni raccomandate (allerte attive)": lo scope e' gia'
  // dichiarato nel titolo, quindi resta corretto restringerlo alle sole attive.
  // Il KPI "Raccomandazioni con azione eseguita" e' calcolato altrove su un campione piu' ampio,
  // proprio per evitare che vada a 0%/— non appena non ci sono piu' allerte attive (caso normale a fine
  // scenario, non un'anomalia) mentre lo storico recente mostra azioni eseguite.
  try {
    const raccomandazioni = await GrapeHealthAPI.getRaccomandazioni({});
    const lista = estraiContenuto(raccomandazioni);
    renderAzioniChart('chartAzioni', lista);
  } catch (err) {
    console.error('Errore nel recupero delle raccomandazioni per il grafico azioni', err);
  }
}

// Le due domande operative piu' dirette per chi deve decidere se e dove intervenire oggi:
// su quante parcelle c'e' almeno un problema in corso (non solo quante allerte in totale, che
// puo' confondere un problema diffuso su una parcella con uno sparso su tutte), e quante di
// quelle allerte sono gia' al livello di rischio piu' alto. Aggiornati sulla stessa cadenza del
// pannello Allerte (12s, v. alerts.js), non su quella del grafico misurazioni: a differenza dei
// due KPI che sostituiscono, la loro utilita' non dipende da quale parametro o parcella sia
// selezionato nel grafico in quel momento.
function aggiornaKpiParcelleESeverita(allerteAttive) {
  const parcelleColpite = new Set(allerteAttive.map(a => a.parcella)).size;
  const totaleParcelle = Object.keys(PARCELLE_INFO).length;
  document.getElementById('kpiParcelleAllerta').textContent = totaleParcelle > 0
    ? `${parcelleColpite}/${totaleParcelle}`
    : String(parcelleColpite);

  const severe = allerteAttive.filter(a => a.livelloRischio === 'severo').length;
  document.getElementById('kpiAllerteSevere').textContent = severe;
}

function aggiornaKpiMisurazioni(ultimaRicevutaIl) {
  document.getElementById('kpiUltimaRicevuta').textContent = ultimaRicevutaIl
    ? new Date(ultimaRicevutaIl).toLocaleTimeString('it-IT')
    : '—';
}

const NUMERO_ALLERTE_PER_TEMPO_RISPOSTA = 20;
const INTERVALLO_TEMPO_RISPOSTA_MS = 60000;

let timerTempoRisposta = null;

async function aggiornaTempoRisposta() {
  const kpiTempo = document.getElementById('kpiTempoRisposta');
  const kpiTasso = document.getElementById('kpiTassoEsecuzione');
  try {
    const risposta = await GrapeHealthAPI.getAllerte({ stato: 'risolta', size: NUMERO_ALLERTE_PER_TEMPO_RISPOSTA });
    const risolte = estraiContenuto(risposta);
    aggiornaKpiRicorrenza(risolte);

    // Campione per il tasso di esecuzione: le attive gia' in memoria (aggiornate
    // dal polling di alerts.js) piu' le ultime risolte appena recuperate. Cosi'
    // il KPI non crolla a 0%/— appena non ci sono piu' allerte attive (fine
    // scenario), pur restando limitato a un campione recente per lo stesso
    // motivo di contenimento chiamate gia' applicato al tempo di risposta.
    // Deduplicato con un Set: i due elenchi sono aggiornati da cicli di polling
    // indipendenti (12s per le attive, 60s per le risolte), quindi un'allerta
    // appena transitata da attiva a risolta puo' comparire in entrambi finche'
    // il prossimo giro di refreshAllerteAttive() non la rimuove dalle attive. 
    // L'endpoint non ne risentirebbe (l'IN della query SQL deduplica comunque), ma evitare
    // id ripetuti tiene la richiesta pulita e il codice corretto per costruzione.
    const idsCampione = [...new Set([
      ...ultimeAllerteAttive.map(a => a.id),
      ...risolte.map(a => a.id),
    ])];

    if (idsCampione.length === 0) {
      kpiTempo.textContent = '—';
      kpiTasso.textContent = '—';
      document.getElementById('kpiUltimaAzioneEseguita').textContent = '—';
      return;
    }

    // Una sola chiamata per l'intero lotto invece di una per allerta: l'endpoint
    // ignora silenziosamente gli id non trovati, quindi non serve gestire errori
    // per singola allerta. Funziona indipendentemente dallo stato (RaccomandazioniService.perAllerteMultiple),
    // quindi copre in un colpo solo sia le attive sia le risolte del campione.
    const raccomandazioni = await GrapeHealthAPI.getRaccomandazioni({ allertaIds: idsCampione });
    const perAllerta = new Map(raccomandazioni.map(r => [r.allertaId, r]));

    const eseguite = raccomandazioni.filter(r => r.basedOnSimulatedExecution);
    const percentuale = raccomandazioni.length > 0
      ? Math.round((eseguite.length / raccomandazioni.length) * 100)
      : 0;
    // La sola percentuale non dice su quale base e' calcolata: 57% su 3 raccomandazioni
    // e 57% su 30 non comunicano la stessa affidabilita' del dato a chi legge la card.
    kpiTasso.textContent = raccomandazioni.length > 0
      ? `${percentuale}% (${eseguite.length}/${raccomandazioni.length})`
      : '—';

    // Parallelo di kpiUltimaRicevuta (main.js) ma sul lato attuazione invece che
    // sensori: quando il sistema ha eseguito l'ultima azione simulata, non solo
    // quanto spesso lo fa. Stesso campione di 'eseguite' appena calcolato sopra,
    // nessuna chiamata aggiuntiva.
    const ultimaEseguitaIl = eseguite.reduce((max, r) => {
      const t = r.eseguitaIl ? new Date(r.eseguitaIl).getTime() : 0;
      return t > max ? t : max;
    }, 0);
    document.getElementById('kpiUltimaAzioneEseguita').textContent = ultimaEseguitaIl
      ? new Date(ultimaEseguitaIl).toLocaleTimeString('it-IT')
      : '—';

    const durate = risolte
      .map(allerta => {
        const r = perAllerta.get(allerta.id);
        if (!r || !r.basedOnSimulatedExecution || !r.eseguitaIl || !allerta.risoltaIl) return null;
        return new Date(allerta.risoltaIl) - new Date(r.eseguitaIl);
      })
      .filter(ms => ms !== null && ms >= 0);

    kpiTempo.textContent = durate.length > 0
      ? formattaDurata(durate.reduce((somma, ms) => somma + ms, 0) / durate.length)
      : '—';
  } catch (err) {
    console.error('Errore nel calcolo delle metriche di esecuzione/risposta', err);
    kpiTempo.textContent = '—';
    kpiTasso.textContent = '—';
    document.getElementById('kpiUltimaAzioneEseguita').textContent = '—';
  }
}

function avviaAggiornamentoTempoRisposta() {
  aggiornaTempoRisposta();
  timerTempoRisposta = setInterval(aggiornaTempoRisposta, INTERVALLO_TEMPO_RISPOSTA_MS);
}

// Le allerte attive raccontano cosa sta succedendo ora (v. aggiornaKpiParcelleESeverita
// sopra); queste ultime risolte raccontano cosa si e' ripresentato di recente, un'informazione
// complementare che nessun altro pannello mostra: quale tipo di rischio ricorre piu' spesso,
// quale parcella ne e' piu' coinvolta, e quale singolo nodo sensore in particolare - un livello
// di dettaglio piu' fine della sola parcella, utile quando piu' nodi di tipo diverso sulla
// stessa parcella (es. bacca-B1 per il sunburn, idrico-B1 per lo stress idrico) generano allerte
// di tipo diverso e non e' immediato da un conteggio per sola parcella capire quale dei due sia
// davvero il punto debole ricorrente. `nodoCodice` e' un campo di AllertaDTO gia' mostrato per
// ogni singola allerta nella lista, ma prima di questa statistica mai aggregato. Stesso campione
// delle ultime N allerte risolte gia' recuperato per il KPI del tasso di esecuzione sopra:
// nessuna chiamata fetch aggiuntiva, e nessuna nuova logica: vociPiuFrequenti(elementi, campo)
// era gia' generica rispetto al campo da contare.
function aggiornaKpiRicorrenza(risolte) {
  const kpiTipo = document.getElementById('kpiTipoRicorrente');
  const kpiParcella = document.getElementById('kpiParcellaRicorrente');
  const kpiNodo = document.getElementById('kpiNodoRicorrente');
  const tipoPiuFrequente = vociPiuFrequenti(risolte, 'tipo');
  const parcellaPiuFrequente = vociPiuFrequenti(risolte, 'parcella');
  const nodoPiuFrequente = vociPiuFrequenti(risolte, 'nodoCodice');
  kpiTipo.textContent = tipoPiuFrequente ? formatTipo(tipoPiuFrequente) : '—';
  kpiParcella.textContent = parcellaPiuFrequente || '—';
  kpiNodo.textContent = nodoPiuFrequente || '—';
}

// Conta le occorrenze del valore del campo indicato in ciascun elemento e restituisce il valore
// piu' frequente, o null su un array vuoto. A parita' di conteggio vince il primo incontrato
// nell'ordine dato, che per le allerte risolte e' l'ordine di risoluzione dal piu' recente.
function vociPiuFrequenti(elementi, campo) {
  const conteggi = {};
  elementi.forEach(e => { conteggi[e[campo]] = (conteggi[e[campo]] || 0) + 1; });
  let migliore = null;
  let massimo = 0;
  Object.keys(conteggi).forEach(chiave => {
    if (conteggi[chiave] > massimo) { migliore = chiave; massimo = conteggi[chiave]; }
  });
  return migliore;
}

// V. il commento equivalente in api.js per il perche' di questo blocco: nel browser e' un
// no-op (nessun `module`), in Node espone a stats.test.js la sola funzione di questo file
// che non tocca il DOM o una fetch.
if (typeof module !== 'undefined' && module.exports) {
  module.exports = { vociPiuFrequenti };
}