import json
import os

import ollama
from dotenv import load_dotenv
from openai import OpenAI
from pydantic import BaseModel, Field


class ContractData(BaseModel):
    is_contract: bool
    provider: str = Field(default="Unbekannt")
    contractType: str = Field(default="Unbekannt")
    monthlyCost: float = Field(default=0.0)
    endDate: str = Field(default="2099-12-31")

class ContractDataOpenai(BaseModel):
    is_contract: bool = Field(
        description="True, wenn es sich um einen Vertrag, eine Rechnung oder Police handelt. False bei Spam/Werbung.")
    provider: str = Field(
        description="Der Name des Anbieters (z.B. Telekom, Vattenfall, Allianz). 'Unbekannt', falls nicht auffindbar.")
    contractType: str = Field(description="Die Kategorie (z.B. Internet, Strom, Versicherung, Streaming).")
    monthlyCost: float = Field(
        description="Die monatlichen Kosten in Euro als Zahl. Wenn es ein Jahresbeitrag ist, teile ihn durch 12.")
    endDate: str = Field(
        description="Das Vertragsende oder die nächste Kündigungsfrist im Format YYYY-MM-DD. Wenn unbekannt: '2099-12-31'")

def analyze_text_with_ollama(extracted_text: str) -> ContractData:
    prompt = f"""
        Du bist ein hochpräziser KI-Datenextraktions-Experte für den deutschen Markt.
Deine einzige Aufgabe ist es, den folgenden unstrukturierten OCR-Text (aus einem gescannten PDF) zu analysieren.

WICHTIGE REGELN:
1. is_contract: Setze dies zwingend auf true (in lower case), sobald der Text Elemente wie Rechnungsnummer, Kundennummer, Tarif, Beitrag, Miete, Kündigungsfrist, Police oder ähnliches enthält. Es ist fast immer true, außer es ist offensichtlich ein Kochrezept oder Spam.
2. provider: Suche nach dem Absender oder dem Firmennamen (z.B. Telekom, Vodafone, Allianz, Stadtwerke). Ignoriere den Namen des Kunden/Empfängers.
3. contractType: Klassifiziere den Vertrag. Nutze z.B.: "Internet", "Mobilfunk", "Strom", "Gas", "Versicherung", "Miete", "Fitnessstudio", "Abonnement".
4. monthlyCost: Finde den Preis. Suche nach "Gesamtbetrag", "Rechnungsbetrag", "monatlich". WICHTIG: Wenn dort "jährlich" oder "Jahresbeitrag" steht, MUSST du diese Zahl durch 12 teilen. Antworte nur mit einer Zahl (Nutze den Punkt als Komma, z.B. 39.99).
5. endDate: Suche nach "Kündigung zum", "Laufzeit bis", "Vertragsende". Formatiere es streng als YYYY-MM-DD. Wenn absolut nicht zu finden, nimm "2099-12-31".

Antworte AUSSCHLIESSLICH mit einem validen JSON-Objekt. Kein Text davor, kein Text danach.

Hier ist der Text:
        {extracted_text}
        """
    try:
        response = ollama.chat(
            model='llama3',
            messages=[{'role': 'user', 'content': prompt}],
            format='json'
        )

        response_text = response['message']['content']
        data_dict = json.loads(response_text)

        return ContractData(**data_dict)

    except Exception as e:
        print(f"Fehler bei der Ollama-Analyse: {e}")
        return ContractData(is_contract=False)


def analyze_text_with_openai(extracted_text: str) -> ContractDataOpenai:
    load_dotenv()
    client = OpenAI(api_key=os.getenv("OPENAI_API_KEY"))
    completion = client.beta.chat.completions.parse(
        model="gpt-4o-mini",
        messages=[
            {"role": "system",
             "content": "Du bist ein hochpräziser Assistent für Vertragsmanagement. Deine Aufgabe ist es, deutsche Verträge und Rechnungen zu analysieren und strukturierte Daten zu extrahieren. Halte dich streng an das vorgegebene Format."},
            {"role": "user", "content": f"Bitte analysiere den folgenden Vertragstext:\n\n{extracted_text}"}
        ],
        response_format=ContractDataOpenai
    )
    result = completion.choices[0].message.parsed
    return result





