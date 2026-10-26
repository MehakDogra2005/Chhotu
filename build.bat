@echo off
rem Builds everything into ONE file: backend\target\backend-0.0.1.jar
rem That single file serves both the website and the API. Run it with:  java -jar backend\target\backend-0.0.1.jar

cd /d %~dp0frontend
if not exist node_modules call npm install
call npm run build || exit /b 1

cd /d %~dp0backend
call mvnw.cmd -q -DskipTests package || exit /b 1

echo.
echo Done. Start the app with:  java -jar backend\target\backend-0.0.1.jar
echo Then open http://localhost:8080
