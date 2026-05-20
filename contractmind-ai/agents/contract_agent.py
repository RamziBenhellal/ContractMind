from haystack import Pipeline
from haystack.components.builders import PromptBuilder
from haystack_integrations.components.generators.ollama import OllamaGenerator
import json


class ContractAgent:
    def __init__(self):
        self.pipeline = Pipeline()

        # 1. ANPASSUNG: Wir nutzen jetzt {{ text }} anstelle von {{ documents[0].content }}
        prompt_template = """
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
        {{ text }}
        """
        self.prompt_builder = PromptBuilder(template=prompt_template)

        self.llm = OllamaGenerator(
            model="llama3",
            url="http://localhost:11434",
            timeout=6000,
            generation_kwargs={"format": "json"},
        )

        # 2. ANPASSUNG: pdf_converter entfernt, wir fügen nur noch Prompt und LLM hinzu
        self.pipeline.add_component("prompt_builder", self.prompt_builder)
        self.pipeline.add_component("llm", self.llm)

        # 3. ANPASSUNG: Direkte Verbindung vom Prompt zum LLM
        self.pipeline.connect("prompt_builder", "llm")

        print("✅ Contract Agent (Llama 3 Text-Pipeline) initialisiert!")

    # 4. ANPASSUNG: Input ist jetzt ein String (text) anstatt ein Dateipfad
    def process_document(self, text: str):

        # Wir übergeben den Text direkt an den PromptBuilder
        results = self.pipeline.run({
            "prompt_builder": {"text": text}
        })

        ai_response_string = results["llm"]["replies"][0]

        # Kleiner Sicherheitsgurt, falls die Antwort leer oder ungültig ist
        return json.loads(ai_response_string)
