FROM python:3.11-slim

WORKDIR /app

# Kopiere die Abhängigkeiten und installiere sie
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

# Kopiere den restlichen Code
COPY . .

# Port freigeben (falls FastAPI/Flask genutzt wird, oft 8000 oder 5000)
EXPOSE 8000

# Startbefehl (passe dies an, wie dein main.py gestartet wird)
CMD ["python", "main.py"]