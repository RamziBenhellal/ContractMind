from fastapi import FastAPI
from api.contract import router as contract_router
from api.chat import router as chat_router
import uvicorn

app = FastAPI(title="ContractMind AI Service", version="1.0")

app.include_router(contract_router)
app.include_router(chat_router)

@app.get("/")
def health_check():
    return {"status": "ok", "message": "ContractMind AI Server läuft!"}

if __name__ == "__main__":
    uvicorn.run("main:app", host="0.0.0.0", port=8000, reload=True)
