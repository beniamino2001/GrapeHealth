export function randItem(arr) {
  return arr[Math.floor(Math.random() * arr.length)];
}

export function randSample(arr, n) {
  const copia = [...arr];
  const campione = [];
  while (campione.length < n && copia.length > 0) {
    const indice = Math.floor(Math.random() * copia.length);
    campione.push(copia.splice(indice, 1)[0]);
  }
  return campione;
}