import os
import tempfile

from fastapi import APIRouter, UploadFile, File, HTTPException
from agents.contract_agent import ContractAgent
import pdfplumber

from pathlib import Path


router = APIRouter(prefix="/api/contracts", tags=["Contracts"])

ai_agent = ContractAgent()


@router.post("/analyze")
async def analyze_contract(file: UploadFile = File(...)):
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
        # 2. Dem Agenten den absoluten Pfad übergeben
        print("🤖 Sende Text an Ollama (das kann lokal ein paar Sekunden dauern)...")
        ai_json = ai_agent.process_document(extracted_text)
        print("✅ KI Analyse erfolgreich.")
        result = ai_json
        print(result)
        return {
            "status": "success",
            "data": result  # Wandelt das Pydantic-Objekt sauber in ein Dictionary um
        }

    except Exception as e:
        print(f"❌ Fehler bei der Verarbeitung: {e}")
        # Wenn hier ein Fehler passiert, sehen wir ihn genau im Terminal!
        raise HTTPException(status_code=500, detail=str(e))

