from haystack import Pipeline
from haystack.components.builders import PromptBuilder
from haystack.components.converters import PyPDFToDocument
from haystack_integrations.components.generators.ollama import OllamaGenerator
import json


class ContractAgent:
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

                Antworte AUSSCHLIESSLICH mit dem JSON-Objekt.
                """
        self.prompt_builder = PromptBuilder(template=prompt_template)
        self.llm = OllamaGenerator(
            model="llama3",
            url="http://localhost:11434",
            timeout=6000,
            generation_kwargs={"format": "json"},

        )

        self.pipeline.add_component("pdf_converter", self.pdf_converter)
        self.pipeline.add_component("prompt_builder", self.prompt_builder)
        self.pipeline.add_component("llm", self.llm)

        self.pipeline.connect("pdf_converter.documents", "prompt_builder.documents")
        self.pipeline.connect("prompt_builder", "llm")
        print("✅ Contract Agent (Llama 3) initialisiert!")

    def process_document(self, file_path:str):

        results = self.pipeline.run({"pdf_converter": {"sources": [file_path]}})
        ai_response_string = results["llm"]["replies"][0]

        return json.loads(ai_response_string)

