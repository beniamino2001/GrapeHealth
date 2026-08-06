Chart.defaults.font.family = "-apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif";
Chart.defaults.color = '#4a4a44';
Chart.defaults.borderColor = '#e8e5d8';

const COLORI_PARCELLA = {
  parcellaA: '#5b8c5a',
  parcellaB: '#4a7ba6',
  parcellaC: '#c0785a',
};

const MAX_PUNTI_PER_SERIE = 400;

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

function renderTrendChart(canvasId, misurazioni, parametro, parcellaSelezionata, unitaMisura) {
  const ctx = document.getElementById(canvasId).getContext('2d');
  if (trendChart) trendChart.destroy();

  const titoloAsseY = `${parametro.replaceAll('_', ' ')}${unitaMisura ? ` (${unitaMisura})` : ''}`;
  let datasets;

  if (parcellaSelezionata) {
    const punti = decima(
      [...misurazioni].sort((a, b) => new Date(a.rilevatoIl) - new Date(b.rilevatoIl)),
      MAX_PUNTI_PER_SERIE
    );
    datasets = [{
      label: parcellaSelezionata,
      data: punti.map(m => ({ x: new Date(m.rilevatoIl).getTime(), y: m.valore })),
      borderColor: COLORI_PARCELLA[parcellaSelezionata] || '#5b8c5a',
      backgroundColor: 'rgba(91,140,90,0.15)',
      tension: 0.2, fill: true, pointRadius: 2,
    }];
  } else {
    const perParcella = {};
    misurazioni.forEach(m => { (perParcella[m.parcella] ||= []).push(m); });

    datasets = Object.keys(perParcella).sort().map(parcella => {
      const punti = decima(
        [...perParcella[parcella]].sort((a, b) => new Date(a.rilevatoIl) - new Date(b.rilevatoIl)),
        MAX_PUNTI_PER_SERIE
      );
      return {
        label: parcella,
        data: punti.map(m => ({ x: new Date(m.rilevatoIl).getTime(), y: m.valore })),
        borderColor: COLORI_PARCELLA[parcella] || '#999',
        backgroundColor: 'transparent',
        tension: 0.2, pointRadius: 2,
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

let alertTypeChart = null;

const COLORI_TIPO = {
  stress_idrico: '#5b8c5a',
  ondata_di_calore: '#c0785a',
  sunburn: '#d9a441',
  tre_dieci: '#8a4f7d',
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
  const ordineLivelli = ['lieve', 'moderato', 'alto', 'severo', 'critico'];
  const conteggi = {};
  allerte.forEach(a => {
    const l = (a.livelloRischio || 'non specificato').toLowerCase();
    conteggi[l] = (conteggi[l] || 0) + 1;
  });
  const livelli = Object.keys(conteggi).sort((a, b) => ordineLivelli.indexOf(a) - ordineLivelli.indexOf(b));

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