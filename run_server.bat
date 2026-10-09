@echo off
chcp 65001 >nul
title AutoClip Server - Sincronizador de Portapapeles & Escritura En Vivo

echo ====================================================================
echo   🚀 AutoClip - Servidor para Windows
echo ====================================================================
echo.

:: Detectar Python o py launcher
set PYTHON_CMD=
where python >nul 2>nul
if %errorlevel% equ 0 (
    set PYTHON_CMD=python
) else (
    where py >nul 2>nul
    if %errorlevel% equ 0 (
        set PYTHON_CMD=py
    )
)

if "%PYTHON_CMD%"=="" (
    echo [ERROR] No se encontro Python instalado en el sistema.
    echo Por favor descarga e instala Python desde: https://www.python.org/downloads/
    echo Asegurate de marcar la casilla "Add python.exe to PATH" durante la instalacion.
    echo.
    pause
    exit /b 1
)

echo [✓] Python detectado: %PYTHON_CMD%
echo [*] Verificando dependencias (pynput, qrcode, pyperclip)...
%PYTHON_CMD% -m pip install -q -r requirements.txt 2>nul
if %errorlevel% neq 0 (
    echo [i] Nota: Si falta alguna libreria opcional, puedes instalarla con: pip install -r requirements.txt
)

echo [*] Iniciando servidor AutoClip en Windows...
echo.
%PYTHON_CMD% server\clipsync_server.py

if %errorlevel% neq 0 (
    echo.
    echo [!] El servidor se ha detenido inesperadamente.
    pause
)
