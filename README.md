# 📋 AutoClip Pro - Sincronizador de Portapapeles & Escritura En Vivo (Android ➔ PC)

**AutoClip Pro** es una solución avanzada que conecta tu teléfono Android directamente con tu computadora, permitiendo:
1. 📲 **Uso en segundo plano** con notificación permanente y botones de acceso directo.
2. 📷 **Sincronización instantánea mediante Código QR** (con soporte para recordar y alternar entre distintas computadoras).
3. ⚡ **Modo En Vivo**: Escribe en tu celular y el texto se escribirá en tiempo real directamente en cualquier campo o ventana activa de tu computadora.

---

## 🚀 Inicio Rápido

### 1. Iniciar el Servidor en tu Computadora
En la terminal de tu computadora, ejecuta:
```bash
./run_server.sh
```
*(o también: `python3 server/clipsync_server.py`)*

El servidor mostrará un **código QR en la terminal** y las direcciones IP disponibles de tu red local.

---

## 📱 Funcionalidades Principales

### 1. 📲 Segundo Plano con Botones en la Barra de Notificaciones
Puedes activar el **Servicio en Segundo Plano** desde Ajustes:
- Mantiene una notificación persistente con 4 acciones rápidas:
  - 📋 **Pegar y Enviar**: Lee el portapapeles de tu celular y lo envía de inmediato a la PC sin tener que abrir la app.
  - ⚡ **Modo En Vivo**: Alterna entre sincronización de portapapeles y modo escritura directa.
  - 🧹 **Limpiar**: Borra el portapapeles de la PC.
  - ⏹ **Cerrar**: Detiene el servicio en segundo plano cuando no lo necesites.

### 2. 📷 Sincronización Rápida con Código QR
- En la app Android, pulsa el botón **Escanear QR 📷** en la barra superior.
- Apunta la cámara al **código QR en tu terminal** o en el panel web (`http://localhost:8765`).
- La app se conectará automáticamente y guardará la computadora en tu lista de **"Mis Computadoras"** para alternar entre ellas con 1 toque.

### 3. ⚡ Modo En Vivo: Escritura Directa en la PC
- **Modo Portapapeles (Ctrl+V)**: Todo lo que escribas se copia al portapapeles del sistema operativo de tu PC.
- **Modo En Vivo (Escritura Directa)**: Conforme escribes en tu celular (o dictas por voz), se simulan las pulsaciones de teclado directamente en el campo de texto o programa que tengas enfocado en la PC (Word, Discord, Navegador, Terminal, etc.).
- **Selector de Campos en la Web**: En el panel web (`http://localhost:8765`), puedes hacer clic en cualquier campo objetivo (Chat, Notas, Código) y verás cómo se escribe en vivo.

---

## 🛠 Instalación del APK en tu Celular

Para compilar e instalar en tu celular conectado por USB:
```bash
adb install -r android-app/app/build/outputs/apk/debug/app-debug.apk
```
o mediante:
```bash
cd android-app && ./gradlew installDebug
```
