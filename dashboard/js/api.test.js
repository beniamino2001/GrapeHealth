const { test } = require('node:test');
const assert = require('node:assert/strict');
const { buildQuery, estraiContenuto, formattaDurata, escapeHtml } = require('./api.js');

// --- buildQuery -------------------------------------------------------------

test('buildQuery su un oggetto vuoto restituisce una stringa vuota, senza punto interrogativo', () => {
  assert.equal(buildQuery({}), '');
});

test('buildQuery ignora null, undefined e stringa vuota', () => {
  assert.equal(buildQuery({ a: null, b: undefined, c: '', d: 'valore' }), '?d=valore');
});

test('buildQuery codifica un parametro singolo con il punto interrogativo iniziale', () => {
  assert.equal(buildQuery({ parcella: 'parcellaA' }), '?parcella=parcellaA');
});

test('buildQuery unisce piu\' parametri con "&", nell\'ordine delle chiavi', () => {
  assert.equal(buildQuery({ parametro: 'pioggia', size: 50 }), '?parametro=pioggia&size=50');
});

test('buildQuery codifica correttamente caratteri speciali (spazi, "&")', () => {
  const query = buildQuery({ nota: 'a & b c' });
  assert.equal(query, `?nota=${encodeURIComponent('a & b c')}`);
  assert.ok(!query.includes(' '));
});

test('buildQuery ripete la chiave per ogni elemento di un valore array (es. allertaIds)', () => {
  assert.equal(buildQuery({ allertaIds: [1, 2, 3] }), '?allertaIds=1&allertaIds=2&allertaIds=3');
});

test('buildQuery su un array vuoto non produce alcuna coppia per quella chiave', () => {
  assert.equal(buildQuery({ allertaIds: [], parcella: 'parcellaA' }), '?parcella=parcellaA');
});

// --- estraiContenuto ---------------------------------------------------------

test('estraiContenuto su un array semplice lo restituisce invariato', () => {
  const arr = [{ id: 1 }, { id: 2 }];
  assert.equal(estraiContenuto(arr), arr);
});

test('estraiContenuto su una pagina Spring Data ({content: [...]}) restituisce il solo content', () => {
  const pagina = { content: [{ id: 1 }], page: { totalElements: 1 } };
  assert.deepEqual(estraiContenuto(pagina), [{ id: 1 }]);
});

test('estraiContenuto su un singolo oggetto (ne\' array ne\' pagina) lo racchiude in un array di un elemento', () => {
  const oggetto = { id: 42 };
  assert.deepEqual(estraiContenuto(oggetto), [oggetto]);
});

test('estraiContenuto su null o undefined restituisce un array vuoto', () => {
  assert.deepEqual(estraiContenuto(null), []);
  assert.deepEqual(estraiContenuto(undefined), []);
});

// --- formattaDurata -----------------------------------------------------------

test('formattaDurata restituisce "—" per input negativo o non finito', () => {
  assert.equal(formattaDurata(-1), '—');
  assert.equal(formattaDurata(NaN), '—');
  assert.equal(formattaDurata(Infinity), '—');
});

test('formattaDurata su 0ms restituisce "0s"', () => {
  assert.equal(formattaDurata(0), '0s');
});

test('formattaDurata sotto il minuto mostra solo i secondi', () => {
  assert.equal(formattaDurata(45000), '45s');
});

test('formattaDurata tra un minuto e un\'ora mostra minuti e secondi', () => {
  assert.equal(formattaDurata(125000), '2m 5s');
});

test('formattaDurata su un\'ora esatta mostra "1h 0m", non piu\' i secondi', () => {
  assert.equal(formattaDurata(60 * 60 * 1000), '1h 0m');
});

test('formattaDurata oltre l\'ora mostra ore e minuti, azzerando i minuti oltre l\'ora corrente', () => {
  assert.equal(formattaDurata((2 * 60 + 5) * 60 * 1000), '2h 5m');
});

// --- escapeHtml ---------------------------------------------------------------

test('escapeHtml neutralizza i cinque caratteri speciali HTML', () => {
  assert.strictEqual(escapeHtml(`<script>alert('x')</script> & "cita"`),
    '&lt;script&gt;alert(&#39;x&#39;)&lt;/script&gt; &amp; &quot;cita&quot;');
});

test('escapeHtml su null o undefined restituisce una stringa vuota', () => {
  assert.strictEqual(escapeHtml(null), '');
  assert.strictEqual(escapeHtml(undefined), '');
});

test('escapeHtml lascia invariato un testo senza caratteri speciali', () => {
  assert.strictEqual(escapeHtml('parcellaA'), 'parcellaA');
});