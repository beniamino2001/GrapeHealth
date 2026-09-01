// Bootstrap della dashboard: popolamento dei filtri dal catalogo parcelle e
// dall'elenco parametri, caricamento iniziale e aggiornamento periodico del
// grafico delle misurazioni, con la guardia anti-race-condition su richieste
// concorrenti (v. commento su generazioneGraficoMisurazioni sotto). Una sola
// funzione pura in questo file, formattaData (v. main.test.js): il resto resta
// intrecciato con il DOM e/o con una chiamata fetch.
let PARCELLE_INFO = {}; // nome -> ParcellaDTO, popolato al bootstrap da /api/parcelle

// I nove valori devono coincidere con il vincolo CHECK su misurazione.parametro
// (schema del database) e con i parametri realmente pubblicati dai nodi del
// simulatore: un valore qui che non esiste nei dati non produce errori, solo
// un'opzione nel filtro che non restituira' mai nulla; un parametro pubblicato
// ma assente da questo elenco resta invece irraggiungibile dal grafico.
// velocita_vento, temperatura_suolo e umidita_suolo sono qui per lo stesso
// motivo di pioggia a suo tempo: dati gia' pubblicati e gia' a database, privi
// altrimenti di qualunque punto di consultazione su questa dashboard.
const PARAMETRI = ['temperatura_aria', 'umidita_aria', 'pioggia', 'bagnatura_fogliare', 'psi_stem', 'temperatura_bacca', 'velocita_vento', 'temperatura_suolo', 'umidita_suolo'];

// Le chiavi devono coincidere esattamente con i value delle <option> di
// #filtroFinestra in index.html: e' quel valore, letto dal <select>, a
// indicizzare questo oggetto in caricaGraficoMisurazioni(). `ms: null` per
// "breve" segnala l'assenza di finestra (modalita' "ultime letture").
const FINESTRE_TEMPORALI = {
  breve: { ms: null },
  giorno: { ms: 24 * 60 * 60 * 1000 },
  tre_giorni: { ms: 3 * 24 * 60 * 60 * 1000 },
  settimana: { ms: 7 * 24 * 60 * 60 * 1000 },
};

async function popolaFiltri() {
  const selParcella = document.getElementById('filtroParcella');
  const selParametro = document.getElementById('filtroParametro');
  const selRisolteParcella = document.getElementById('filtroRisolteParcella');

  try {
    const parcelle = await GrapeHealthAPI.getParcelle();
    parcelle.forEach(p => { PARCELLE_INFO[p.nome] = p; });
  } catch (err) {
    console.error('Impossibile caricare il catalogo parcelle da /api/parcelle', err);
    // I filtri restano utilizzabili sul solo parametro; nessuna opzione parcella oltre a "Tutte".
  }

  Object.keys(PARCELLE_INFO).sort().forEach(nome => {
    const opt = document.createElement('option');
    opt.value = nome; opt.textContent = nome;
    selParcella.appendChild(opt);

    const optRisolte = document.createElement('option');
    optRisolte.value = nome; optRisolte.textContent = nome;
    selRisolteParcella.appendChild(optRisolte);
  });

  PARAMETRI.forEach(p => {
    const opt = document.createElement('option');
    opt.value = p;
    opt.textContent = p.replaceAll('_', ' ');
    selParametro.appendChild(opt);
  });
}

// Contatore di generazione: ogni invocazione di caricaGraficoMisurazioni()
// incrementa questo valore e ne cattura una copia locale. Se, al termine di
// un await, la copia locale non e' piu' la piu' recente, significa che nel
// frattempo e' partita un'altra invocazione (es. l'auto-refresh a 30s che si
// sovrappone a un click su "Aggiorna grafico", o a se stessa quando una fetch
// impiega piu' di 30s con --time-scale elevati): in tal caso la risposta e'
// scartata invece di essere renderizzata. Senza questo codice, due chiamate
// concorrenti possono entrambe arrivare a renderTrendChart() e finire per
// creare due istanze Chart.js sullo stesso canvas (una delle due "vince" la
// destroy() dell'altra nell'ordine sbagliato), con conseguente doppia scala e
// dataset residuo sovrapposti.
let generazioneGraficoMisurazioni = 0;

async function caricaGraficoMisurazioni() {
  const generazioneCorrente = ++generazioneGraficoMisurazioni;
  const erroreEl = document.getElementById('measurementError');
  erroreEl.textContent = '';

  const parcella = document.getElementById('filtroParcella').value || undefined;
  const parametro = document.getElementById('filtroParametro').value || 'temperatura_aria';
  const finestra = FINESTRE_TEMPORALI[document.getElementById('filtroFinestra').value];
  const finestraAttiva = Boolean(finestra.ms);

  try {
    let dal, al, size;

    if (finestraAttiva) {
      const ultima = await GrapeHealthAPI.getMisurazioni({ parcella, parametro, size: 1 });
      if (generazioneCorrente !== generazioneGraficoMisurazioni) return; // superata da una richiesta piu' recente

      const ultimeMisurazioni = estraiContenuto(ultima);
      if (ultimeMisurazioni.length === 0) {
        erroreEl.textContent = 'Nessuna misurazione disponibile per i filtri selezionati.';
        renderTrendChart('chartMisurazioni', [], parametro, parcella, undefined, finestraAttiva);
        aggiornaKpiMisurazioni(null);
        return;
      }
      al = new Date(ultimeMisurazioni[0].rilevatoIl);
      dal = new Date(al.getTime() - finestra.ms);
      size = 2000; // tetto di sicurezza (limite di default di Spring Data)
    } else {
      size = parcella ? 100 : 300;
    }

    const risposta = await GrapeHealthAPI.getMisurazioni({
      parcella, parametro, size,
      dal: dal ? dal.toISOString() : undefined,
      al: al ? al.toISOString() : undefined,
    });
    if (generazioneCorrente !== generazioneGraficoMisurazioni) return; // superata da una richiesta piu' recente

    const misurazioni = estraiContenuto(risposta);

    if (misurazioni.length === 0) {
      erroreEl.textContent = 'Nessuna misurazione disponibile per i filtri selezionati.';
    }

    const unitaMisura = misurazioni[0]?.unitaMisura;
    const ultimaRicevutaIl = misurazioni.reduce((max, m) => {
      const t = new Date(m.ricevutoIl).getTime();
      return t > max ? t : max;
    }, 0);

    renderTrendChart('chartMisurazioni', misurazioni, parametro, parcella, unitaMisura, finestraAttiva);
    aggiornaKpiMisurazioni(ultimaRicevutaIl || null);
  } catch (err) {
    if (generazioneCorrente !== generazioneGraficoMisurazioni) return; // superata da una richiesta piu' recente
    console.error('Errore nel recupero delle misurazioni', err);
    erroreEl.textContent = `Impossibile caricare i dati: ${err.message}`;
  }
}

// Anagrafica dei nodi sensore (/api/nodi, nuovo endpoint): un nodo rimosso
// dalla configurazione viene marcato attivo=false invece di essere cancellato,
// per non perdere la storia delle misurazioni gia' raccolte - un conteggio
// "operativi su totali" segnala quindi se l'intera rete sensori e' schierata
// o se qualche nodo e' stato messo fuori servizio, un'informazione di stato
// dell'infrastruttura distinta da qualunque cosa riguardi le allerte. Recuperata
// una sola volta al bootstrap, non ripetuta a ogni ciclo: a differenza delle
// allerte, lo stato di un nodo non cambia entro la durata di una sessione.
// Lo stesso array popola anche il dettaglio a scomparsa sotto il KPI, con
// tutti i campi che /api/nodi restituisce: nessuna seconda chiamata fetch.
async function aggiornaKpiNodiOperativi() {
  const kpiNodi = document.getElementById('kpiNodiOperativi');
  const corpoTabella = document.getElementById('corpoTabellaNodi');
  try {
    const nodi = await GrapeHealthAPI.getNodi();
    const operativi = nodi.filter(n => n.attivo).length;
    kpiNodi.textContent = nodi.length > 0 ? `${operativi}/${nodi.length}` : '—';
    renderTabellaNodi(corpoTabella, nodi);
  } catch (err) {
    console.error('Impossibile caricare l\'anagrafica nodi da /api/nodi', err);
    kpiNodi.textContent = '—';
    corpoTabella.innerHTML = '<tr><td colspan="5">Impossibile caricare l\'elenco dei nodi.</td></tr>';
  }
}

// Ordinata per parcella e poi per codice, cosi' che i nodi della stessa parcella
// compaiano vicini - piu' utile a un operatore che scorre la tabella per zona
// che l'ordine grezzo restituito dall'API.
function renderTabellaNodi(corpoTabella, nodi) {
  if (nodi.length === 0) {
    corpoTabella.innerHTML = '<tr><td colspan="5">Nessun nodo censito.</td></tr>';
    return;
  }
  const righe = [...nodi]
    .sort((a, b) => a.parcella.localeCompare(b.parcella) || a.codice.localeCompare(b.codice))
    .map(n => `
      <tr>
        <td>${escapeHtml(n.codice)}</td>
        <td>${escapeHtml(n.tipoNodo)}</td>
        <td>${escapeHtml(n.parcella)}</td>
        <td><span class="badge ${n.attivo ? 'badge-attivo' : 'badge-inattivo'}">${n.attivo ? 'attivo' : 'inattivo'}</span></td>
        <td>${formattaData(n.dataInstallazione)}</td>
      </tr>
    `)
    .join('');
  corpoTabella.innerHTML = righe;
}

// LocalDate lato Java (es. "2025-03-12") non porta con se' alcun fuso orario:
// passarla a `new Date(...)` per poi formattarla localmente introdurrebbe un
// fuso che il dato non ha, con il rischio concreto di mostrare il giorno
// sbagliato in un fuso indietro rispetto a UTC (mezzanotte UTC diventa il
// giorno prima). Riformattata direttamente sulla stringa, senza passare da
// un oggetto Date.
function formattaData(iso) {
  if (!iso) return '—';
  const [anno, mese, giorno] = iso.split('-');
  return `${giorno}/${mese}/${anno}`;
}

// A differenza del pannello Allerte, il grafico delle misurazioni veniva caricato solo all'avvio o al click su
// "Aggiorna grafico": il KPI derivato ("Ultima misurazione ricevuta") restava
// quindi fermo allo snapshot iniziale anche con dati nuovi in arrivo. Risolto
// con un setInterval che richiama la funzione gia' esistente, rispettando i
// filtri correnti selezionati dall'utente.
const INTERVALLO_AGGIORNAMENTO_GRAFICO_MS = 30000;
let timerGraficoMisurazioni = null;

function avviaAggiornamentoGraficoMisurazioni() {
  timerGraficoMisurazioni = setInterval(caricaGraficoMisurazioni, INTERVALLO_AGGIORNAMENTO_GRAFICO_MS);
}

document.addEventListener('DOMContentLoaded', async () => {
  await popolaFiltri();
  aggiornaKpiNodiOperativi();
  inizializzaTabAllerte();

  document.getElementById('filtroForm').addEventListener('submit', e => {
    e.preventDefault();
    caricaGraficoMisurazioni();
  });

  caricaGraficoMisurazioni();
  avviaAggiornamentoGraficoMisurazioni();
  startPolling();
  avviaAggiornamentoTempoRisposta();
});

// V. il commento equivalente in api.js per il perche' di questo blocco: nel browser e' un
// no-op (nessun `module`), in Node espone a main.test.js la sola funzione di questo file
// che non tocca il DOM o una fetch.
if (typeof module !== 'undefined' && module.exports) {
  module.exports = { formattaData };
}