# Builds Galla into one small image: the React website is built first and placed inside the Java server,
# so a single process serves both the website and the API (the same thing build.bat does on a PC).
# Used by Render (and anything else that runs Docker). Settings and secrets come from environment variables,
# never from files in the image: see README.md ("Putting it online").

# ---- 1. the website ----------------------------------------------------------------------------
FROM node:22-alpine AS web
WORKDIR /app/frontend
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci
COPY frontend/ ./
# vite.config.js writes the finished site to ../backend/src/main/resources/static
RUN npm run build

# ---- 2. the Java server (with the website inside) ----------------------------------------------
FROM maven:3.9-eclipse-temurin-17 AS server
WORKDIR /app/backend
COPY backend/pom.xml ./
COPY backend/src ./src
COPY --from=web /app/backend/src/main/resources/static ./src/main/resources/static
RUN mvn -q -B -DskipTests package

# ---- 3. what actually runs ---------------------------------------------------------------------
FROM eclipse-temurin:17-jre
# The server starts in /app/backend: its database and recordings go in ./data, and the sample data is read from ../db
WORKDIR /app/backend
COPY --from=server /app/backend/target/backend-0.0.1.jar ./app.jar
COPY db /app/db
EXPOSE 8080
# Render says which port to use in $PORT. -XX:MaxRAMPercentage keeps Java inside the memory of a small instance.
CMD ["sh", "-c", "exec java -XX:MaxRAMPercentage=50 -Dserver.port=${PORT:-8080} -jar app.jar"]
