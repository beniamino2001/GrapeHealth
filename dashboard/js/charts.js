// Istanze e configurazione di tutti i grafici Chart.js della dashboard: il
// grafico dell'andamento delle misurazioni nel tempo, e i tre grafici
// aggregati del pannello statistiche (parcella, livello di rischio, azioni
// raccomandate). Ogni funzione render* distrugge l'istanza precedente dello
// stesso canvas prima di crearne una nuova, per evitare sovrapposizioni.

Chart.defaults.font.family = "-apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif";
Chart.defaults.color = '#4a4a44';
Chart.defaults.borderColor = '#e8e5d8';

// Colore identificativo per ciascuna parcella nei grafici multi-serie. Una
// parcella non censita qui (v. COLORI_TIPO sotto per lo stesso principio
// applicato ai tipi di allerta) riceve un grigio neutro invece di un colore
// scelto a caso, cosi' resta comunque distinguibile nel grafico e in legenda.
const COLORI_PARCELLA = {
  parcellaA: '#5b8c5a',
  parcellaB: '#4a7ba6',
  parcellaC: '#c0785a',
};

// Riduce una serie di punti a un massimo prestabilito mantenendo un campionamento uniforme nel tempo (passo costante), preservando però l'ultimo punto della serie
function decima(punti, massimo) {
  if (punti.length <= massimo) return punti;
  const passo = Math.ceil(punti.length / massimo);
  const risultato = punti.filter((_, indice) => indice % passo === 0);
  const ultimo = punti[punti.length - 1];
  if (risultato[risultato.length - 1] !== ultimo) risultato.push(ultimo);
  return risultato;
}

let trendChart = null;
const MAX_PUNTI_SENZA_FINESTRA = 400;
const MAX_PUNTI_CON_FINESTRA = 150; // meno punti su finestre ampie: leggibilita' prima di densita'

function renderTrendChart(canvasId, misurazioni, parametro, parcellaSelezionata, unitaMisura, finestraAttiva = false) {
  const ctx = document.getElementById(canvasId).getContext('2d');
  if (trendChart) trendChart.destroy();

  const titoloAsseY = `${parametro.replaceAll('_', ' ')}${unitaMisura ? ` (${unitaMisura})` : ''}`;
  const maxPunti = finestraAttiva ? MAX_PUNTI_CON_FINESTRA : MAX_PUNTI_SENZA_FINESTRA;
  // Con una finestra temporale attiva (giorno/3 giorni/settimana) i marcatori
  // individuali vengono nascosti: su centinaia di punti producono solo una
  // nuvola confusa, la linea da sola comunica meglio l'andamento.
  const raggioPunto = finestraAttiva ? 0 : 2;

  let datasets;

  if (parcellaSelezionata) {
    const punti = decima(
      [...misurazioni].sort((a, b) => new Date(a.rilevatoIl) - new Date(b.rilevatoIl)),
      maxPunti
    );
    datasets = [{
      label: parcellaSelezionata,
      data: punti.map(m => ({ x: new Date(m.rilevatoIl).getTime(), y: m.valore })),
      borderColor: COLORI_PARCELLA[parcellaSelezionata] || '#5b8c5a',
      backgroundColor: 'rgba(91,140,90,0.15)',
      tension: 0.2, fill: true, pointRadius: raggioPunto,
    }];
  } else {
    const perParcella = {};
    misurazioni.forEach(m => { (perParcella[m.parcella] ||= []).push(m); });

    datasets = Object.keys(perParcella).sort().map(parcella => {
      const punti = decima(
        [...perParcella[parcella]].sort((a, b) => new Date(a.rilevatoIl) - new Date(b.rilevatoIl)),
        maxPunti
      );
      return {
        label: parcella,
        data: punti.map(m => ({ x: new Date(m.rilevatoIl).getTime(), y: m.valore })),
        borderColor: COLORI_PARCELLA[parcella] || '#999',
        backgroundColor: 'transparent',
        tension: 0.2, pointRadius: raggioPunto,
      };
    });
  }

  trendChart = new Chart(ctx, {
    type: 'line',
    data: { datasets },
    options: {
      responsive: true,
      maintainAspectRatio: false,
      scales: {
        x: {
          type: 'linear',
          ticks: {
            maxTicksLimit: 8,
            callback: valore => new Date(valore).toLocaleString('it-IT', {
              day: '2-digit', month: '2-digit', hour: '2-digit', minute: '2-digit',
            }),
          },
        },
        y: { beginAtZero: false, title: { display: true, text: titoloAsseY } },
      },
      plugins: {
        legend: { display: true },
        tooltip: {
          callbacks: {
            title: items => items.length ? new Date(items[0].parsed.x).toLocaleString('it-IT') : '',
          },
        },
      },
    },
  });
}

// Mostra un messaggio testuale al posto del canvas quando non ci sono dati da
// rappresentare (es. nessuna allerta attiva): senza questo controllo Chart.js
// lascia semplicemente l'area bianca, indistinguibile da un problema di
// rendering.
function gestisciStatoVuotoGrafico(canvasId, vuoto) {
  const canvas = document.getElementById(canvasId);
  const wrapper = canvas.closest('.chart-wrapper');
  let placeholder = wrapper.querySelector('.chart-empty-state');
  if (!placeholder) {
    placeholder = document.createElement('p');
    placeholder.className = 'chart-empty-state empty-state';
    placeholder.textContent = 'Nessun dato da mostrare.';
    wrapper.appendChild(placeholder);
  }
  // visibility, non display: il canvas mantiene lo spazio occupato, cosi'
  // Chart.js non deve ricalcolare le dimensioni quando i dati tornano.
  canvas.style.visibility = vuoto ? 'hidden' : 'visible';
  placeholder.style.display = vuoto ? 'flex' : 'none';
}

let alertTypeChart = null;

// Popolato da renderAlertTypeChart() sotto, letto da alerts.js (filtraPerTipoAllerta,
// refreshAllerteAttive) per risalire dal tipo cliccato sulla ciambella all'elenco di
// allerte di quel tipo: condiviso fra i due file tramite lo scope globale di window,
// come formattaDurata in api.js. Dichiarato qui esplicitamente invece di lasciarlo
// creare come globale implicito dalla sola assegnazione dentro la funzione.
let ultimeAllertePerTipo = {};

// Stesso principio di COLORI_PARCELLA sopra, applicato ai sette tipi di allerta.
const COLORI_TIPO = {
  stress_idrico: '#5b8c5a',
  ondata_di_calore: '#c0785a',
  sunburn: '#d9a441',
  tre_dieci: '#8a4f7d',
  svernamento_oospore: '#8b6f47',
  infezione_secondaria: '#2f7a7a',
  danno_radicale: '#7a3b2e',
};

function renderAlertTypeChart(canvasId, allerte) {
  const ctx = document.getElementById(canvasId).getContext('2d');
  const conteggi = {};
  ultimeAllertePerTipo = {};

  allerte.forEach(a => {
    conteggi[a.tipo] = (conteggi[a.tipo] || 0) + 1;
    (ultimeAllertePerTipo[a.tipo] ||= []).push(a);
  });

  const tipiGrezzi = Object.keys(conteggi);
  const colori = tipiGrezzi.map(t => COLORI_TIPO[t] || '#999');

  gestisciStatoVuotoGrafico(canvasId, tipiGrezzi.length === 0);

  if (alertTypeChart) alertTypeChart.destroy();

  alertTypeChart = new Chart(ctx, {
    type: 'doughnut',
    data: {
      labels: tipiGrezzi.map(formatTipo),
      datasets: [{ data: tipiGrezzi.map(t => conteggi[t]), backgroundColor: colori }],
    },
    options: {
      responsive: true,
      maintainAspectRatio: false,
      plugins: { legend: { position: 'bottom' } },
      onClick: (event, elements) => {
        if (elements.length === 0) return;
        const tipo = tipiGrezzi[elements[0].index];
        filtraPerTipoAllerta(tipo);
      },
      onHover: (event, elements) => {
        event.native.target.style.cursor = elements.length ? 'pointer' : 'default';
      },
    },
  });
}

function formatTipo(tipo) {
  const mappa = {
    stress_idrico: 'Stress idrico',
    ondata_di_calore: 'Ondata di calore',
    sunburn: 'Sunburn',
    tre_dieci: 'Regola del tre-dieci',
    svernamento_oospore: 'Svernamento oospore',
    infezione_secondaria: 'Infezione secondaria',
    danno_radicale: 'Danno radicale',
  };
  return mappa[tipo] || tipo;
}

let alertParcellaChart = null;
let alertLivelloChart = null;
let azioniChart = null;

function renderAlertParcellaChart(canvasId, allerte) {
  const ctx = document.getElementById(canvasId).getContext('2d');
  const conteggi = {};
  allerte.forEach(a => { conteggi[a.parcella] = (conteggi[a.parcella] || 0) + 1; });
  const parcelle = Object.keys(conteggi).sort();

  gestisciStatoVuotoGrafico(canvasId, parcelle.length === 0);

  if (alertParcellaChart) alertParcellaChart.destroy();
  alertParcellaChart = new Chart(ctx, {
    type: 'bar',
    data: {
      labels: parcelle,
      datasets: [{ label: 'Allerte attive', data: parcelle.map(p => conteggi[p]), backgroundColor: '#5b8c5a' }],
    },
    options: {
      responsive: true,
      maintainAspectRatio: false,
      scales: { y: { beginAtZero: true, ticks: { stepSize: 1 } } },
      plugins: { legend: { display: false } },
    },
  });
}

function renderAlertLivelloChart(canvasId, allerte) {
  const ctx = document.getElementById(canvasId).getContext('2d');
  const ordineLivelli = ['moderato', 'severo'];
  const conteggi = {};
  allerte.forEach(a => {
    const l = (a.livelloRischio || 'non specificato').toLowerCase();
    conteggi[l] = (conteggi[l] || 0) + 1;
  });
  const livelli = Object.keys(conteggi).sort((a, b) => ordineLivelli.indexOf(a) - ordineLivelli.indexOf(b));

  gestisciStatoVuotoGrafico(canvasId, livelli.length === 0);

  if (alertLivelloChart) alertLivelloChart.destroy();
  alertLivelloChart = new Chart(ctx, {
    type: 'bar',
    data: {
      labels: livelli,
      datasets: [{ label: 'Allerte attive', data: livelli.map(l => conteggi[l]), backgroundColor: '#c0785a' }],
    },
    options: {
      responsive: true,
      maintainAspectRatio: false,
      indexAxis: 'y',
      scales: { x: { beginAtZero: true, ticks: { stepSize: 1 } } },
      plugins: { legend: { display: false } },
    },
  });
}

function renderAzioniChart(canvasId, raccomandazioni) {
  const ctx = document.getElementById(canvasId).getContext('2d');
  const conteggi = {};
  raccomandazioni.forEach(r => {
    const azione = r.azioneConsigliata || 'non specificata';
    conteggi[azione] = (conteggi[azione] || 0) + 1;
  });
  const azioni = Object.keys(conteggi).sort();

  gestisciStatoVuotoGrafico(canvasId, azioni.length === 0);

  if (azioniChart) azioniChart.destroy();
  azioniChart = new Chart(ctx, {
    type: 'bar',
    data: {
      labels: azioni,
      datasets: [{ label: 'Allerte attive', data: azioni.map(a => conteggi[a]), backgroundColor: '#8a4f7d' }],
    },
    options: {
      responsive: true,
      maintainAspectRatio: false,
      indexAxis: 'y',
      scales: { x: { beginAtZero: true, ticks: { stepSize: 1 } } },
      plugins: { legend: { display: false } },
    },
  });
}

// V. il commento equivalente in api.js per il perche' di questo blocco: nel
// browser e' un no-op (nessun `module`), in Node espone a charts.test.js le
// due sole funzioni di questo file che non toccano il DOM o Chart.js.
if (typeof module !== 'undefined' && module.exports) {
  module.exports = { decima, formatTipo };
  global.formatTipo = formatTipo;
}