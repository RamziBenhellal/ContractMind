import os
import tempfile

from fastapi import APIRouter, UploadFile, File, HTTPException
from agents.contract_agent import ContractAgent
from pathlib import Path


router = APIRouter(prefix="/api/contracts", tags=["Contracts"])

ai_agent = ContractAgent()


@router.post("/analyze")
async def analyze_contract(file: UploadFile = File(...)):
    print(f"📥 Empfange Datei: {file.filename}")

    # 1. Eine sichere, eindeutige temporäre Datei mit absolutem Pfad erstellen
    with tempfile.NamedTemporaryFile(delete=False, suffix=".pdf") as temp_file:
        content = await file.read()
        temp_file.write(content)
        # Hier holen wir uns den echten, absoluten Pfad vom Betriebssystem
        temp_file_path = temp_file.name

    print(f"📂 Temporärer Pfad generiert: {temp_file_path}")

    try:
        # 2. Dem Agenten den absoluten Pfad übergeben
        ai_json = ai_agent.process_document(temp_file_path)
        print("✅ KI Analyse erfolgreich.")
        return ai_json

    except Exception as e:
        print(f"❌ Fehler bei der Verarbeitung: {e}")
        # Wenn hier ein Fehler passiert, sehen wir ihn genau im Terminal!
        raise HTTPException(status_code=500, detail=str(e))

    finally:
        # 3. Aufräumen: Datei vom Betriebssystem löschen
        if os.path.exists(temp_file_path):
            os.remove(temp_file_path)
            print(f"🧹 Temporäre Datei gelöscht.")