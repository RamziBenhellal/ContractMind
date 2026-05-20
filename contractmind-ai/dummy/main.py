from fastapi import FastAPI, UploadFile, File, HTTPException
import uvicorn
import os
import pdfplumber
from openai import OpenAI
from dotenv import load_dotenv


from dummy.contract_data import ContractData
from dummy.ai_service import analyze_text_with_ollama
load_dotenv()
client = OpenAI(api_key=os.getenv("OPENAI_API_KEY"))

app = FastAPI(title="ContractMind AI Service", version="1.0")

@app.get("/")
def read_root():
    return {"Message": "ContractMind AI Service is running 🚀"}
@app.post("/analyze_contract")
async def analyse(file: UploadFile = File(...)):
    if not file.filename.endswith(".pdf"):
        raise HTTPException(status_code=404, detail="Only PDF files supported.")
    try:
        extracted_text =""
        with pdfplumber.open(file.file) as pdf:
            for page in pdf.pages:
                text = page.extract_text()
                if text:
                    extracted_text += text + "\n"
        if not extracted_text.strip():
            raise HTTPException(status_code=404, detail="No text extracted.")
        completion = client.beta.chat.completions.parse(
            model= "gpt-4o-mini",
            messages=[
                {"role": "system",
                 "content": "Du bist ein hochpräziser Assistent für Vertragsmanagement. Deine Aufgabe ist es, deutsche Verträge und Rechnungen zu analysieren und strukturierte Daten zu extrahieren. Halte dich streng an das vorgegebene Format."},
                {"role": "user", "content": f"Bitte analysiere den folgenden Vertragstext:\n\n{extracted_text}"}
            ],
            response_format=ContractData
        )
        result = completion.choices[0].message.parsed
        if not result.is_contract:
            return {"status": "error", "message": "Das Dokument scheint kein gültiger Vertrag zu sein."}
        return {
        "status": "success",
        "data": result.model_dump()
        }
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))


@app.post("/analyze")
async def analyze(file: UploadFile = File(...)):
    if not file.filename.endswith(".pdf"):
        raise HTTPException(status_code=400, detail="Bitte lade nur PDF-Dateien hoch.")

    try:
        print(f"📥 Empfange Datei: {file.filename}")

        # 1. Text aus dem PDF extrahieren
        extracted_text = ""
        with pdfplumber.open(file.file) as pdf:
            for page in pdf.pages:
                text = page.extract_text()
                if text:
                    extracted_text += text + "\n"

        if not extracted_text.strip():
            raise HTTPException(status_code=400, detail="Das PDF ist leer oder ein reines Bild.")

        print("🤖 Sende Text an Ollama (das kann lokal ein paar Sekunden dauern)...")

        # 2. Unsere ausgelagerte Methode aufrufen!
        result = analyze_text_with_ollama(extracted_text)

        # 3. Ergebnis prüfen und zurückschicken
        if not result.is_contract:
            print("❌ Kein Vertrag erkannt.")
            return {"status": "error", "message": "Das Dokument scheint kein gültiger Vertrag zu sein."}

        print("✅ Analyse erfolgreich!")
        print("Provider: "+ result.provider)
        print(result.model_dump())

        return {
            "status": "success",
            "data": result.model_dump()  # Wandelt das Pydantic-Objekt sauber in ein Dictionary um
        }
    except Exception as e:
        print(f"🔥 Kritischer Fehler: {e}")
        raise HTTPException(status_code=500, detail=str(e))

if __name__ == "__main__":
    uvicorn.run("main:app", host="0.0.0.0", port=8000, reload=True)
