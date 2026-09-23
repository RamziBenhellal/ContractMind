from __future__ import annotations

import logging
import urllib.request
from dataclasses import dataclass
from pathlib import Path

from rapidfuzz import fuzz

logger = logging.getLogger(__name__)

# Öffentliche BLZ/FinTS-Liste (hbci4j). FinTS/HBCI ist der deutsche Standard –
# nicht weltweite Banken und nicht jede Direktbank bietet ihn noch an.
BLZ_PROPERTIES_URL = (
    "https://raw.githubusercontent.com/hbci4j/hbci4java/master/src/main/resources/blz.properties"
)
CACHE_PATH = Path(__file__).resolve().parents[2] / "data" / "blz.properties"
SEARCH_LIMIT = 40

# Aktuelle Namen, die in älteren BLZ-Dateien noch den alten Institutsnamen tragen.
NAME_OVERRIDES = {
    "25950130": "Sparkasse Hildesheim Goslar Peine",
}


@dataclass(frozen=True)
class BankRecord:
    blz: str
    name: str
    bic: str
    fints_url: str
    location: str = ""


# Fallback, falls die große Liste nicht geladen werden kann.
KNOWN_BANKS: tuple[BankRecord, ...] = (
    BankRecord("25950130", "Sparkasse Hildesheim Goslar Peine", "NOLADE21HIK",
               "https://banking-ni2.s-fints-pt-ni.de/fints30", "Hildesheim"),
    BankRecord("50050201", "Frankfurter Sparkasse", "HELADEF1822",
               "https://banking-hs6.s-fints-pt-hs.de/fints30", "Frankfurt am Main"),
    BankRecord("50010517", "ING-DiBa", "INGDDEFFXXX", "https://fints.ing.de/fints", "Frankfurt am Main"),
    BankRecord("12030000", "Deutsche Kreditbank", "BYLADEM1001", "https://fints.dkb.de/fints", "Berlin"),
    BankRecord("50070010", "Deutsche Bank Frankfurt", "DEUTDEFFXXX", "https://fints.deutsche-bank.de", "Frankfurt am Main"),
    BankRecord("50040000", "Commerzbank Frankfurt", "COBADEFFXXX", "https://fints.commerzbank.de/fints", "Frankfurt am Main"),
    BankRecord("50090500", "Sparda-Bank Hessen", "GENODEF1S12",
               "https://fints2.atruvia.de/cgi-bin/hbciservlet", "Frankfurt am Main"),
    BankRecord("20050550", "Hamburger Sparkasse", "HASPDEHHXXX",
               "https://banking-hh1.s-fints-pt-hh.de/fints30", "Hamburg"),
    BankRecord("37040044", "Commerzbank Köln", "COBADEFFXXX", "https://fints.commerzbank.de/fints", "Köln"),
    BankRecord("10050000", "Landesbank Berlin / Berliner Sparkasse", "BELADEBE",
               "https://banking-be6.s-fints-pt-be.de/fints30", "Berlin"),
    BankRecord("76050101", "Sparkasse Nürnberg", "SSKNDE77XXX",
               "https://banking-by4.s-fints-pt-by.de/fints30", "Nürnberg"),
    BankRecord("25050180", "Sparkasse Hannover", "SPKHDE2HXXX",
               "https://banking-ni1.s-fints-pt-ni.de/fints30", "Hannover"),
    BankRecord("26050001", "Sparkasse Göttingen", "NOLADE21GOE",
               "https://banking-ni2.s-fints-pt-ni.de/fints30", "Göttingen"),
    BankRecord("25151270", "Stadtsparkasse Hameln", "NOLADE21HMS",
               "https://banking-ni2.s-fints-pt-ni.de/fints30", "Hameln"),
    BankRecord("25850110", "Sparkasse Celle-Gifhorn-Wolfsburg", "NOLADE21GFW",
               "https://banking-ni2.s-fints-pt-ni.de/fints30", "Gifhorn"),
)


def _normalize(text: str) -> str:
    table = str.maketrans({"ä": "ae", "ö": "oe", "ü": "ue", "ß": "ss", "é": "e"})
    return " ".join((text or "").lower().translate(table).split())


class BankDirectory:
    def __init__(self, banks: tuple[BankRecord, ...] | None = None) -> None:
        self._banks = banks if banks is not None else self._load_banks()

    def search(self, query: str) -> list[BankRecord]:
        needle = _normalize(query)
        if len(needle) < 2:
            return []

        scored: list[tuple[int, BankRecord]] = []
        for bank in self._banks:
            hay = _normalize(f"{bank.blz} {bank.name} {bank.bic} {bank.location}")
            tokens = [token for token in needle.split() if token]
            if needle in bank.blz or needle in _normalize(bank.bic):
                score = 100
            elif needle in _normalize(bank.name):
                score = 95
            elif needle in hay:
                score = 85
            elif tokens and all(token in hay for token in tokens):
                score = 88
            else:
                fuzzy = max(
                    fuzz.partial_ratio(needle, _normalize(bank.name)),
                    fuzz.partial_ratio(needle, _normalize(f"{bank.name} {bank.location}")),
                )
                if fuzzy < 82:
                    continue
                score = int(fuzzy)
            scored.append((score, bank))

        scored.sort(key=lambda item: (-item[0], item[1].name))
        return [bank for _, bank in scored[:SEARCH_LIMIT]]

    def resolve(self, blz: str | None, bic: str | None, fints_url: str | None) -> BankRecord | None:
        if fints_url and (blz or bic):
            match = self._by_blz(blz) or self._by_bic(bic)
            return BankRecord(
                blz=blz or (match.blz if match else ""),
                name=match.name if match else (blz or bic or "Bank"),
                bic=bic or (match.bic if match else ""),
                fints_url=fints_url,
                location=match.location if match else "",
            )
        if fints_url:
            return BankRecord(blz=blz or "", name=blz or bic or "Bank", bic=bic or "", fints_url=fints_url)
        return self._by_blz(blz) or self._by_bic(bic)

    def _by_blz(self, blz: str | None) -> BankRecord | None:
        if not blz:
            return None
        clean = blz.strip()
        return next((bank for bank in self._banks if bank.blz == clean), None)

    def _by_bic(self, bic: str | None) -> BankRecord | None:
        if not bic:
            return None
        clean = bic.strip().upper()
        return next((bank for bank in self._banks if bank.bic.upper() == clean), None)

    def _load_banks(self) -> tuple[BankRecord, ...]:
        by_blz: dict[str, BankRecord] = {bank.blz: bank for bank in KNOWN_BANKS}
        for bank in self._parse_properties(self._ensure_cache()):
            if bank.blz in NAME_OVERRIDES:
                bank = BankRecord(
                    blz=bank.blz,
                    name=NAME_OVERRIDES[bank.blz],
                    bic=bank.bic or by_blz.get(bank.blz, bank).bic,
                    fints_url=bank.fints_url or by_blz.get(bank.blz, bank).fints_url,
                    location=bank.location,
                )
            by_blz[bank.blz] = bank
        return tuple(by_blz.values())

    def _ensure_cache(self) -> Path | None:
        if CACHE_PATH.exists():
            return CACHE_PATH
        try:
            CACHE_PATH.parent.mkdir(parents=True, exist_ok=True)
            urllib.request.urlretrieve(BLZ_PROPERTIES_URL, CACHE_PATH)
            return CACHE_PATH
        except OSError as exc:
            logger.warning("FinTS-Bankverzeichnis konnte nicht geladen werden: %s", exc)
            return None

    def _parse_properties(self, path: Path | None) -> list[BankRecord]:
        if path is None or not path.exists():
            return []
        banks: list[BankRecord] = []
        for raw in path.read_text(encoding="utf-8", errors="replace").splitlines():
            line = raw.strip()
            if not line or line.startswith("#") or "=" not in line:
                continue
            blz, rest = line.split("=", 1)
            parts = rest.split("|")
            if len(parts) < 6:
                continue
            fints_url = parts[5].strip()
            if not fints_url.startswith("http"):
                continue
            name = NAME_OVERRIDES.get(blz.strip(), parts[0].strip())
            banks.append(
                BankRecord(
                    blz=blz.strip(),
                    name=name,
                    bic=parts[2].strip(),
                    fints_url=fints_url,
                    location=parts[1].strip(),
                )
            )
        return banks
