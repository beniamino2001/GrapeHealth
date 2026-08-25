const { test } = require('node:test');
const assert = require('node:assert/strict');
const { vociPiuFrequenti } = require('./stats.js');

test('vociPiuFrequenti su un array vuoto restituisce null', () => {
  assert.equal(vociPiuFrequenti([], 'tipo'), null);
});

test('vociPiuFrequenti individua il valore piu\' frequente per il campo indicato', () => {
  const elementi = [{ tipo: 'sunburn' }, { tipo: 'stress_idrico' }, { tipo: 'sunburn' }];
  assert.equal(vociPiuFrequenti(elementi, 'tipo'), 'sunburn');
});

test('vociPiuFrequenti a parita\' di conteggio restituisce il primo incontrato nell\'ordine dato', () => {
  const elementi = [{ parcella: 'parcellaB' }, { parcella: 'parcellaA' }];
  assert.equal(vociPiuFrequenti(elementi, 'parcella'), 'parcellaB');
});

test('vociPiuFrequenti funziona su un campo qualunque, non solo tipo o parcella', () => {
  const elementi = [{ livelloRischio: 'moderato' }, { livelloRischio: 'severo' }, { livelloRischio: 'moderato' }];
  assert.equal(vociPiuFrequenti(elementi, 'livelloRischio'), 'moderato');
});

test('vociPiuFrequenti su un solo elemento lo restituisce come piu\' frequente', () => {
  assert.equal(vociPiuFrequenti([{ tipo: 'tre_dieci' }], 'tipo'), 'tre_dieci');
});