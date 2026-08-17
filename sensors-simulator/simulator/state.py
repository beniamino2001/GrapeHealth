"""
Persistenza dello stato di sessione fra un'esecuzione e l'altra del
simulatore.

Ad ogni avvio di `python -m simulator.main` si concatenano più sessioni
(anche con scenari diversi) come un'unica storia coerente, a meno che non
venga specificato il parametro --reset-sessione.

Lo stato salvato qui è locale alla macchina (cartella .state/, esclusa da
Git) e va considerato usa-e-getta: se il file manca, è corrotto, o si
riferisce a un formato che questo modulo non riconosce più, il simulatore
si comporta esattamente come se non fosse mai esistito, ripartendo da
"adesso" con le condizioni di default.
"""

import json
import logging
from datetime import datetime
from pathlib import Path
from typing import Optional

logger = logging.getLogger("grapehealth.simulator")

STATE_PATH = Path(__file__).resolve().parent.parent / ".state" / "sessione_simulata.json"


def carica_stato_sessione() -> Optional[dict]:
    """Ritorna il dict {"ultimo_timestamp_simulato": str, "parcelle": {...}}
    dell'ultima sessione salvata, o None se non esiste o non è leggibile."""
    if not STATE_PATH.exists():
        return None
    try:
        dati = json.loads(STATE_PATH.read_text(encoding="utf-8"))
        # validazione minima: deve avere il campo indispensabile per procedere,
        # e deve essere un timestamp isoformat valido.
        datetime.fromisoformat(dati["ultimo_timestamp_simulato"])
        return dati
    except (json.JSONDecodeError, KeyError, ValueError, OSError) as exc:
        logger.warning(
            "Stato di sessione precedente non leggibile o non valido (%s): riparto da 'adesso'.",
            exc,
        )
        return None


def salva_stato_sessione(ultimo_timestamp_simulato: datetime, stati_parcelle: dict) -> None:
    """`stati_parcelle` è {nome_parcella: StatoParcella.esporta_stato()}."""
    dati = {
        "ultimo_timestamp_simulato": ultimo_timestamp_simulato.isoformat(),
        "parcelle": stati_parcelle,
    }
    try:
        STATE_PATH.parent.mkdir(parents=True, exist_ok=True)
        # scrittura atomica: file temporaneo + rename, così un'interruzione brusca
        # a metà scrittura non lascia mai un JSON troncato al posto di quello valido della sessione precedente.
        percorso_temporaneo = STATE_PATH.with_suffix(".tmp")
        percorso_temporaneo.write_text(json.dumps(dati, indent=2), encoding="utf-8")
        percorso_temporaneo.replace(STATE_PATH)
    except OSError as exc:
        logger.warning(
            "Impossibile salvare lo stato di sessione (%s): la prossima esecuzione ripartirà da 'adesso'.",
            exc,
        )


def elimina_stato_sessione() -> None:
    """Usato da --reset-sessione per garantire che non resti in giro uno
    stato pre-reset nel caso il processo termini bruscamente prima del
    primo salvataggio della nuova sessione."""
    try:
        STATE_PATH.unlink(missing_ok=True)
    except OSError:
        pass