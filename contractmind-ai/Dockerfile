# HIER GEÄNDERT: Das volle Image statt der slim-Version
FROM python:3.11

WORKDIR /app

# Kopiere die Abhängigkeiten und installiere sie
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

# Kopiere den restlichen Code
COPY . .

# Port freigeben
EXPOSE 8000

# Startbefehl
CMD ["python", "main.py"]