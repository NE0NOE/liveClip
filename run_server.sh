#!/usr/bin/env bash
# AutoClip - Iniciar Servidor de Sincronización en PC

cd "$(dirname "$0")"
echo "Iniciando AutoClip Server en segundo plano..."
python3 server/clipsync_server.py
