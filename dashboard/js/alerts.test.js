const { test } = require('node:test');
const assert = require('node:assert/strict');

// testoRisoluzionePrevista() chiama formattaDurata() come funzione globale,
// esattamente come farebbe nel browser dove entrambi gli script condividono
// `window`: richiedere prima api.js (che la espone su `global`, v. il
// commento in fondo a quel file) replica la stessa condizione in Node.
require('./api.js');
const { formattaOperatore, testoRisoluzionePrevista } = require('./alerts.js');

// --- formattaOperatore --------------------------------------------------------

test('formattaOperatore converte gli operatori di confronto larghi nel simbolo unicode', () => {
  assert.equal(formattaOperatore('<='), '≤');
  assert.equal(formattaOperatore('>='), '≥');
});

test('formattaOperatore lascia invariati gli operatori stretti e l\'uguaglianza', () => {
  assert.equal(formattaOperatore('<'), '<');
  assert.equal(formattaOperatore('>'), '>');
  assert.equal(formattaOperatore('='), '=');
});

test('formattaOperatore su un operatore non mappato lo restituisce invariato', () => {
  assert.equal(formattaOperatore('!='), '!=');
});

// --- testoRisoluzionePrevista --------------------------------------------------

test('testoRisoluzionePrevista su un istante futuro riporta il conto alla rovescia', () => {
  const tra5Minuti = new Date(Date.now() + 5 * 60 * 1000).toISOString();
  const testo = testoRisoluzionePrevista(tra5Minuti);
  assert.match(testo, /^Risoluzione prevista: .+ \(tra circa 5m 0s\)$/);
});

test('testoRisoluzionePrevista su un istante gia\' passato riporta "(a breve)", non un conto alla rovescia negativo', () => {
  const dueMinutiFa = new Date(Date.now() - 2 * 60 * 1000).toISOString();
  const testo = testoRisoluzionePrevista(dueMinutiFa);
  assert.match(testo, /\(a breve\)$/);
  assert.ok(!testo.includes('tra circa'));
});

test('testoRisoluzionePrevista include sempre la data/ora formattata in italiano, in entrambi i casi', () => {
  const futuro = testoRisoluzionePrevista(new Date(Date.now() + 60000).toISOString());
  const passato = testoRisoluzionePrevista(new Date(Date.now() - 60000).toISOString());
  assert.match(futuro, /^Risoluzione prevista: \d{1,2}\/\d{1,2}\/\d{4}/);
  assert.match(passato, /^Risoluzione prevista: \d{1,2}\/\d{1,2}\/\d{4}/);
});