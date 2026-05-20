import os
from haystack.components.builders import PromptBuilder
from haystack_integrations.components.generators.ollama import OllamaGenerator

from haystack import Pipeline
from haystack.components.converters import PyPDFToDocument


class ContractAIPipeline:
    def __init__(self):
        self.pipeline = Pipeline()
        self.pdf_converter = PyPDFToDocument()
        prompt_template = """
                Hier ist der extrahierte Text eines Vertrages:
                ---
                {{ documents[0].content }}
                ---

                Bitte analysiere diesen Vertrag und extrahiere die folgenden Informationen strikt im JSON-Format:
                - "provider": Der Name des Anbieters.
                - "contractType": Die Kategorie (z.B. Internet, Strom, Versicherung).
                - "monthlyCost": Die monatlichen Kosten als reine Zahl (z.B. 29.99).
                - "endDate": Das Vertragsende im Format YYYY-MM-DD. (Falls unbefristet, nutze "2099-12-31").

                Antworte AUSSCHLIESSLICH mit dem JSON-Objekt. Kein Markdown, keine Erklärungen.
                """
        self.prompt_builder = PromptBuilder(template=prompt_template)
        self.llm = OllamaGenerator(
            model="llama3",
            url="http://localhost:11434",  # ✅ So ist es richtig!
        timeout = 6000,
        generation_kwargs = {
            "format": "json"
        }
        )
        self.pipeline.add_component("pdf_converter", self.pdf_converter)
        self.pipeline.add_component("prompt_builder",self.prompt_builder)
        self.pipeline.add_component("llm",self.llm)

        self.pipeline.connect("pdf_converter.documents","prompt_builder.documents")
        self.pipeline.connect("prompt_builder","llm")

        print("Haystack-Pipeline mit llama3 ... ")

    def process_document(self, document_path: str):
        print(f"Start processing document: {document_path}")
        print("⚙️ KI arbeitet... (Das kann lokal ein paar Sekunden dauern)")

        results = self.pipeline.run({
            "pdf_converter": {"sources":[document_path]}
        })

        ai_response = results["llm"]["replies"][0]
        print("\n🤖 Antwort der KI:\n", ai_response)

        return ai_response
if __name__ == "__main__":
    ai_service = ContractAIPipeline()
    ai_service.process_document("Nebenkosten.pdf")
