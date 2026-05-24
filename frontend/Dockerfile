# Stage 1: Bauen (Node.js)
FROM node:18-alpine AS build
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci
COPY . .
# Baut das Angular Projekt für Produktion
RUN npm run build --configuration=production

# Stage 2: Ausführen (Nginx)
FROM nginx:alpine
# Kopiere die gebauten Dateien in den Nginx-Ordner.
# WICHTIG: Ersetze "frontend" im Pfad unten durch den "outputPath" aus deiner angular.json (oft 'dist/projektname')
COPY --from=build /app/dist/frontend/browser /usr/share/nginx/html
EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]
