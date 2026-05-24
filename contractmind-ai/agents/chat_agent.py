from haystack import Pipeline
from haystack.components.builders import PromptBuilder
from haystack_integrations.components.generators.ollama import OllamaGenerator


class ChatAgent:
    def __init__(self):
        self.pipeline = Pipeline()
        prompt_template = """
                Du bist der freundliche und kompetente Finanz- und Vertragsassistent der App "ContractMind".
        Antworte immer auf Deutsch, sei hilfreich, kurz und präzise.

        Hier sind die aktuellen Verträge des Nutzers:
        ---
        {{ context }}
        ---

        Frage des Nutzers: {{ question }}
        
        Antwort:
                """
        self.prompt_builder = PromptBuilder(template = prompt_template)
        self.llm = OllamaGenerator(
            model="llama3",
            url="http://localhost:11434",
            timeout=6000
        )

        self.pipeline.add_component("prompt_builder", self.prompt_builder)
        self.pipeline.add_component("llm", self.llm)
        self.pipeline.connect("prompt_builder", "llm")

    def ask_question(self, question: str, context: str):
        print(f"Question: {question}")

        results= self.pipeline.run({
            "prompt_builder": {
                "question": question,
                "context": context},
        })
        return results["llm"]["replies"][0]
