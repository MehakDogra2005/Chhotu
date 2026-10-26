@echo off
rem Starts the backend and the frontend in two windows, then opens the page in your browser.
rem Close both windows to stop the app.

start "Dukaan Saathi - backend" cmd /k "cd /d %~dp0backend && mvnw.cmd spring-boot:run"
start "Dukaan Saathi - frontend" cmd /k "cd /d %~dp0frontend && (if not exist node_modules npm install) && npm run dev -- --open"
