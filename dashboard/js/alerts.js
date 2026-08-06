const POLL_INTERVAL_MS = 12000;
let pollTimer = null;

let ultimeAllerteAttive = [];
let filtroTipoAttuale = null;

let tabAllerteAttuale = 'attiva';
let paginaRisolteCorrente = 0;
let paginaRisolteInfo = { totalPages: 1, totalElements: 0 };

async function refreshAllerteAttive() {
  const erroreEl = document.getElementById('alertError');
  erroreEl.textContent = '';
  try {
    const risposta = await GrapeHealthAPI.getAllerte({ stato: 'attiva', size: 100 });
    const allerte = estraiContenuto(risposta);
    ultimeAllerteAttive = allerte;

    renderAlertTypeChart('chartAllerteTipo', allerte);

    if (tabAllerteAttuale === 'attiva') {
      if (filtroTipoAttuale && ultimeAllertePerTipo[filtroTipoAttuale]) {
        renderAlertList(ultimeAllertePerTipo[filtroTipoAttuale], filtroTipoAttuale);
      } else {
        filtroTipoAttuale = null;
        renderAlertList(allerte);
      }
    }
    // Se si è sulla tab "Risolte", il polling dei dati non tocca la lista visibile: l'utente continua a consultare lo storico senza interruzioni.

    document.getElementById('alertCount').textContent = allerte.length;
    document.getElementById('lastUpdate').textContent = new Date().toLocaleTimeString('it-IT');

    aggiornaStatistiche(allerte);

    return allerte;
  } catch (err) {
    console.error('Errore nel recupero delle allerte attive', err);
    erroreEl.textContent = `Impossibile aggiornare le allerte: ${err.message}`;
  }
}

function filtraPerTipoAllerta(tipo) {
  filtroTipoAttuale = tipo;
  const allerteDelTipo = ultimeAllertePerTipo[tipo] || [];
  renderAlertList(allerteDelTipo, tipo);
  if (allerteDelTipo.length > 0) {
    mostraRaccomandazione(allerteDelTipo[0]); // ora passa l'oggetto, non l'id
  }
}

function resetFiltroTipo() {
  filtroTipoAttuale = null;
  renderAlertList(ultimeAllerteAttive);
}

// --- Tab Attive / Risolte -------------------------------------------------

function inizializzaTabAllerte() {
  document.querySelectorAll('.tab-btn').forEach(btn => {
    btn.addEventListener('click', () => cambiaTabAllerte(btn.dataset.stato));
  });
}

function cambiaTabAllerte(stato) {
  tabAllerteAttuale = stato;
  filtroTipoAttuale = null;
  paginaRisolteCorrente = 0;

  document.querySelectorAll('.tab-btn').forEach(btn =>
    btn.classList.toggle('active', btn.dataset.stato === stato)
  );

  if (stato === 'attiva') {
    document.getElementById('paginazioneAllerte').innerHTML = '';
    renderAlertList(ultimeAllerteAttive);
  } else {
    caricaAllerteRisolte(0);
  }
}

async function caricaAllerteRisolte(page) {
  const container = document.getElementById('alertList');
  container.innerHTML = '<p class="empty-state">Caricamento…</p>';
  try {
    const risposta = await GrapeHealthAPI.getAllerte({ stato: 'risolta', page, size: 10 });
    const allerte = estraiContenuto(risposta);
    paginaRisolteCorrente = risposta.number ?? page;
    paginaRisolteInfo = {
      totalPages: risposta.totalPages ?? 1,
      totalElements: risposta.totalElements ?? allerte.length,
    };
    renderAlertList(allerte);
    renderPaginazione();
  } catch (err) {
    console.error('Errore nel recupero delle allerte risolte', err);
    container.innerHTML = `<p class="error-text">Impossibile caricare lo storico: ${err.message}</p>`;
  }
}

function renderPaginazione() {
  const el = document.getElementById('paginazioneAllerte');
  if (tabAllerteAttuale !== 'risolta' || paginaRisolteInfo.totalPages <= 1) {
    el.innerHTML = '';
    return;
  }
  el.innerHTML = `
    <div class="paginazione-bottoni">
      <button type="button" id="pagPrec" ${paginaRisolteCorrente === 0 ? 'disabled' : ''}>&larr; Precedenti</button>
      <button type="button" id="pagSucc" ${paginaRisolteCorrente + 1 >= paginaRisolteInfo.totalPages ? 'disabled' : ''}>Successive &rarr;</button>
    </div>
    <span class="paginazione-info">Pagina ${paginaRisolteCorrente + 1} di ${paginaRisolteInfo.totalPages} (${paginaRisolteInfo.totalElements} totali)</span>
  `;
  document.getElementById('pagPrec')?.addEventListener('click', () => caricaAllerteRisolte(paginaRisolteCorrente - 1));
  document.getElementById('pagSucc')?.addEventListener('click', () => caricaAllerteRisolte(paginaRisolteCorrente + 1));
}

// --- Lista e dettaglio ------------------------------------------------------

function renderAlertList(allerte, filtroTipoAttivo = null) {
  const container = document.getElementById('alertList');
  container.innerHTML = '';

  if (filtroTipoAttivo) {
    const banner = document.createElement('div');
    banner.className = 'filtro-attivo';
    banner.innerHTML = `Filtro: <strong>${formatTipo(filtroTipoAttivo)}</strong> · <button type="button" class="reset-filtro">mostra tutte</button>`;
    banner.querySelector('.reset-filtro').addEventListener('click', resetFiltroTipo);
    container.appendChild(banner);
  }

  if (allerte.length === 0) {
    container.insertAdjacentHTML('beforeend', '<p class="empty-state">Nessuna allerta in questa categoria.</p>');
    return;
  }

  allerte.forEach(a => {
    const card = document.createElement('div');
    const livello = (a.livelloRischio || '').toLowerCase();
    const risolta = a.stato === 'risolta';
    card.className = `alert-card livello-${livello}${risolta ? ' risolta' : ''}`;
    card.innerHTML = `
      <div class="alert-header">
        <span>${formatTipo(a.tipo)}${risolta ? ' <span class="badge badge-risolta">risolta</span>' : ''}</span>
        <span>${a.livelloRischio || ''}</span>
      </div>
      <div class="alert-body">
        <span>${a.parcella} · ${a.nodoCodice}</span>
        <span>${a.generataIl ? new Date(a.generataIl).toLocaleString('it-IT') : ''}</span>
      </div>
      ${risolta && a.risoltaIl ? `
        <div class="alert-risolta-info">
          Risolta il ${new Date(a.risoltaIl).toLocaleString('it-IT')}
          · dopo ${formattaDurata(new Date(a.risoltaIl) - new Date(a.generataIl))}
        </div>
      ` : ''}
    `;
    card.addEventListener('click', () => mostraRaccomandazione(a));
    container.appendChild(card);
  });
}

async function mostraRaccomandazione(allerta) {
  const panel = document.getElementById('recommendationPanel');
  panel.innerHTML = '<p class="empty-state">Caricamento…</p>';
  try {
    const risposta = await GrapeHealthAPI.getRaccomandazioni({ allertaId: allerta.id });
    const lista = estraiContenuto(risposta);
    renderRaccomandazione(lista[0], allerta);
  } catch (err) {
    console.error('Errore nel recupero della raccomandazione', err);
    panel.innerHTML = `<p class="error-text">Impossibile caricare la raccomandazione: ${err.message}</p>`;
  }
}

function renderRaccomandazione(r, allerta) {
  const panel = document.getElementById('recommendationPanel');
  if (!r) {
    panel.innerHTML = '<p class="empty-state">Nessuna raccomandazione disponibile per questa allerta.</p>';
    return;
  }

  const badge = r.basedOnSimulatedExecution
    ? '<span class="badge badge-eseguita">Azione simulata eseguita</span>'
    : '<span class="badge badge-teorica">Solo raccomandazione teorica</span>';

  const dettaglioAllerta = allerta ? `
    <p class="dettaglio-allerta">
      <strong>Regola scatenante:</strong> ${allerta.regolaScatenante || '—'}<br>
      <strong>Descrizione:</strong> ${allerta.descrizione || '—'}
    </p>
    ${allerta.stato === 'risolta' && allerta.risoltaIl ? `
  <p><strong>Risolta il:</strong> ${new Date(allerta.risoltaIl).toLocaleString('it-IT')}
  ${r.basedOnSimulatedExecution && r.eseguitaIl
        ? `(${formattaDurata(new Date(allerta.risoltaIl) - new Date(r.eseguitaIl))} dopo l'esecuzione)`
        : ''}</p>
` : ''}
  ` : '';

  panel.innerHTML = `
    <h3>Allerta #${r.allertaId ?? '—'} — ${formatTipo(r.tipoAllerta)}</h3>
    ${badge}
    ${dettaglioAllerta}
    <p><strong>Azione consigliata:</strong> ${r.azioneConsigliata || '—'}</p>
    <p>${r.testoRaccomandazione || ''}</p>
    ${r.basedOnSimulatedExecution ? `
      <p><strong>Azione eseguita (simulata):</strong> ${r.azioneEseguita || '—'}</p>
      <p><strong>Esito:</strong> ${r.esitoSimulato || '—'}</p>
      <p><strong>Eseguita il:</strong> ${r.eseguitaIl ? new Date(r.eseguitaIl).toLocaleString('it-IT') : '—'}</p>
    ` : ''}
  `;
}

function startPolling() {
  refreshAllerteAttive();
  pollTimer = setInterval(refreshAllerteAttive, POLL_INTERVAL_MS);
}