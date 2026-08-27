#!/usr/bin/env python3
"""
AutoClip - PC Companion Server
Receives text from Android AutoClip app and automatically injects it into the PC clipboard
or types directly into the selected field / active window on the PC.
Supports Wayland, X11, Windows, and macOS.
"""

import os
import sys
import json
import time
import socket
import threading
import subprocess
from http.server import ThreadingHTTPServer, HTTPServer, BaseHTTPRequestHandler
from urllib.parse import parse_qs, urlparse

# Optional modules
try:
    import qrcode
    import qrcode.image.svg
    HAVE_QRCODE = True
except ImportError:
    HAVE_QRCODE = False

keyboard_controller = None
try:
    from pynput.keyboard import Controller as KeyController
    keyboard_controller = KeyController()
except Exception as e:
    pass

# Global state
PORT = 8765
HOST = "0.0.0.0"
clipboard_lock = threading.Lock()
last_clip_text = ""
last_clip_time = 0
last_notify_time = 0
clip_history = []
MAX_HISTORY = 30

# Live stream subscribers (Server-Sent Events)
sse_subscribers = []
sse_lock = threading.Lock()

# Direct typing toggle (global keystroke simulation)
direct_typing_enabled = True

# Tkinter clipboard instance for reliable background clipboard handling
tk_root = None

def init_clipboard():
    global tk_root
    try:
        import tkinter as tk
        tk_root = tk.Tk()
        tk_root.withdraw()
        return True
    except Exception as e:
        print(f"[!] Tkinter no disponible ({e}), usando métodos nativos del sistema.")
        return False

def broadcast_sse(event_type: str, data: dict):
    with sse_lock:
        dead = []
        payload = f"event: {event_type}\ndata: {json.dumps(data)}\n\n".encode("utf-8")
        for client in sse_subscribers:
            try:
                client.wfile.write(payload)
                client.wfile.flush()
            except Exception:
                dead.append(client)
        for client in dead:
            if client in sse_subscribers:
                sse_subscribers.remove(client)

# Evdev hardware-level keyboard simulation for Wayland & X11
evdev_keyboard = None
try:
    import evdev
    from evdev import ecodes, UInput

    class EvdevKeyboard:
        def __init__(self):
            self.ui = UInput(name='AutoClip-Virtual-Keyboard')
            self._map = {}
            for c in 'abcdefghijklmnopqrstuvwxyz':
                code = getattr(ecodes, f'KEY_{c.upper()}', None)
                if code is not None:
                    self._map[c] = (code, False)
                    self._map[c.upper()] = (code, True)
            for i in range(10):
                code = getattr(ecodes, f'KEY_{i}', None)
                if code is not None:
                    self._map[str(i)] = (code, False)
            self._map[' '] = (ecodes.KEY_SPACE, False)
            self._map['\n'] = (ecodes.KEY_ENTER, False)
            self._map['\t'] = (ecodes.KEY_TAB, False)
            self._map['.'] = (ecodes.KEY_DOT, False)
            self._map[','] = (ecodes.KEY_COMMA, False)
            self._map['-'] = (ecodes.KEY_MINUS, False)
            self._map['='] = (ecodes.KEY_EQUAL, False)
            self._map['/'] = (ecodes.KEY_SLASH, False)
            self._map[';'] = (ecodes.KEY_SEMICOLON, False)
            self._map['\''] = (ecodes.KEY_APOSTROPHE, False)
            self._map['['] = (ecodes.KEY_LEFTBRACE, False)
            self._map[']'] = (ecodes.KEY_RIGHTBRACE, False)
            self._map[chr(92)] = (ecodes.KEY_BACKSLASH, False)
            # Shifted symbols
            self._map[':'] = (ecodes.KEY_SEMICOLON, True)
            self._map['\"'] = (ecodes.KEY_APOSTROPHE, True)
            self._map['<'] = (ecodes.KEY_COMMA, True)
            self._map['>'] = (ecodes.KEY_DOT, True)
            self._map['?'] = (ecodes.KEY_SLASH, True)
            self._map['_'] = (ecodes.KEY_MINUS, True)
            self._map['+'] = (ecodes.KEY_EQUAL, True)
            self._map['!'] = (ecodes.KEY_1, True)
            self._map['@'] = (ecodes.KEY_2, True)
            self._map['#'] = (ecodes.KEY_3, True)
            self._map['$'] = (ecodes.KEY_4, True)
            self._map['%'] = (ecodes.KEY_5, True)
            self._map['^'] = (ecodes.KEY_6, True)
            self._map['&'] = (ecodes.KEY_7, True)
            self._map['*'] = (ecodes.KEY_8, True)
            self._map['('] = (ecodes.KEY_9, True)
            self._map[')'] = (ecodes.KEY_0, True)

        def press_key(self, code, shift=False):
            if shift:
                self.ui.write(ecodes.EV_KEY, ecodes.KEY_LEFTSHIFT, 1)
            self.ui.write(ecodes.EV_KEY, code, 1)
            self.ui.syn()
            self.ui.write(ecodes.EV_KEY, code, 0)
            if shift:
                self.ui.write(ecodes.EV_KEY, ecodes.KEY_LEFTSHIFT, 0)
            self.ui.syn()

        def type_char(self, char):
            if char in self._map:
                code, shift = self._map[char]
                self.press_key(code, shift)
                return True
            return False

        def backspace(self, count=1):
            for _ in range(count):
                self.press_key(ecodes.KEY_BACKSPACE, False)

        def enter(self):
            self.press_key(ecodes.KEY_ENTER, False)

    evdev_keyboard = EvdevKeyboard()
    print("[✓] Teclado virtual hardware (uinput/evdev) inicializado con éxito.")
except Exception as e:
    print(f"[!] Evdev no disponible ({e}), usando controladores de usuario.")

def set_system_clipboard(text: str, notify: bool = True) -> bool:
    global last_clip_text, last_clip_time, last_notify_time, clip_history, tk_root
    now = time.time()
    with clipboard_lock:
        if text == last_clip_text:
            return True
        last_clip_text = text
        last_clip_time = now
        
        # Add to history with 5-second activity debouncing
        if text.strip():
            time_str = time.strftime("%H:%M:%S")
            if not clip_history:
                clip_history.insert(0, {
                    "text": text,
                    "length": len(text),
                    "time": time_str,
                    "timestamp": now
                })
            else:
                last_entry = clip_history[0]
                last_entry_ts = last_entry.get("timestamp", 0)
                # If within 5 seconds of last typing activity, update existing entry
                if now - last_entry_ts < 5.0:
                    last_entry["text"] = text
                    last_entry["length"] = len(text)
                    last_entry["time"] = time_str
                    last_entry["timestamp"] = now
                elif last_entry["text"] != text:
                    # After 5+ seconds of pause, create a new separate history item
                    clip_history.insert(0, {
                        "text": text,
                        "length": len(text),
                        "time": time_str,
                        "timestamp": now
                    })
                    if len(clip_history) > MAX_HISTORY:
                        clip_history.pop()

    success = False

    # Method 1: wl-copy (Wayland native)
    try:
        p = subprocess.Popen(['wl-copy'], stdin=subprocess.PIPE, stderr=subprocess.DEVNULL)
        p.communicate(input=text.encode('utf-8'), timeout=1)
        if p.returncode == 0:
            success = True
    except Exception:
        pass

    # Method 2: xclip (X11 native)
    if not success:
        try:
            p = subprocess.Popen(['xclip', '-selection', 'clipboard'], stdin=subprocess.PIPE, stderr=subprocess.DEVNULL)
            p.communicate(input=text.encode('utf-8'), timeout=1)
            if p.returncode == 0:
                success = True
        except Exception:
            pass

    # Method 3: Tkinter clipboard
    if not success and tk_root is not None:
        try:
            def _tk_copy():
                tk_root.clipboard_clear()
                tk_root.clipboard_append(text)
                tk_root.update()
            tk_root.after(0, _tk_copy)
            success = True
        except Exception as e:
            pass

    # Method 4: pyperclip
    if not success:
        try:
            import pyperclip
            pyperclip.copy(text)
            success = True
        except Exception:
            pass

    # Broadcast to connected PC web pages
    broadcast_sse("clip", {
        "text": text,
        "length": len(text),
        "time": time.strftime("%H:%M:%S")
    })

    # Send subtle desktop notification (throttled to avoid spamming notification daemons)
    preview = text.strip()
    if len(preview) > 50:
        preview = preview[:47] + "..."
    if not preview:
        preview = "[Portapapeles limpiado]"
    
    if notify and (now - last_notify_time > 1.5):
        last_notify_time = now
        def _notify():
            try:
                subprocess.run(
                    ["notify-send", "-a", "AutoClip", "-t", "1200", "-i", "edit-paste",
                     "📋 AutoClip sincronizado", f"{len(text)} caracteres: {preview}"],
                    stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL
                )
            except Exception:
                pass
        threading.Thread(target=_notify, daemon=True).start()

    print(f"\n[+] [{time.strftime('%H:%M:%S')}] Portapapeles PC actualizado ({len(text)} chars):\n    \"{preview}\"")
    return success

last_typed_text = ""
last_typed_lock = threading.Lock()

def compute_delta(old_text: str, new_text: str):
    min_len = min(len(old_text), len(new_text))
    prefix_len = 0
    while prefix_len < min_len and old_text[prefix_len] == new_text[prefix_len]:
        prefix_len += 1
    backspaces = len(old_text) - prefix_len
    to_type = new_text[prefix_len:]
    return backspaces, to_type

def simulate_keyboard_typing(text: str, send_enter: bool = False) -> bool:
    """Types characters incrementally directly into whatever field/window is currently focused on the PC."""
    global last_typed_text, evdev_keyboard

    # Broadcast to web target selector in real-time
    broadcast_sse("type", {
        "text": text,
        "send_enter": send_enter,
        "time": time.strftime("%H:%M:%S")
    })

    # Always keep clipboard synced in background as convenience
    set_system_clipboard(text, notify=False)

    if not direct_typing_enabled:
        return True

    with last_typed_lock:
        if not text:
            last_typed_text = ""
            return True
        backspaces, to_type = compute_delta(last_typed_text, text)
        last_typed_text = text

    if backspaces == 0 and not to_type and not send_enter:
        return True

    success = False

    # Method 1: Hardware-level virtual keyboard via evdev / uinput (Works natively across Wayland & X11)
    if evdev_keyboard is not None:
        try:
            if backspaces > 0:
                evdev_keyboard.backspace(backspaces)
            for char in to_type:
                typed = evdev_keyboard.type_char(char)
                if not typed:
                    # Fallback for unicode/accented letters
                    if keyboard_controller is not None:
                        keyboard_controller.type(char)
            if send_enter:
                evdev_keyboard.enter()
            success = True
        except Exception as e:
            pass

    # Method 2: pynput keyboard controller
    if not success and keyboard_controller is not None:
        try:
            from pynput.keyboard import Key
            for _ in range(backspaces):
                keyboard_controller.press(Key.backspace)
                keyboard_controller.release(Key.backspace)
            if to_type:
                keyboard_controller.type(to_type)
            if send_enter:
                keyboard_controller.press(Key.enter)
                keyboard_controller.release(Key.enter)
            success = True
        except Exception:
            pass

    # Method 3: pyautogui
    if not success:
        try:
            import pyautogui
            if backspaces > 0:
                pyautogui.press('backspace', presses=backspaces)
            if to_type:
                pyautogui.write(to_type, interval=0.001)
            if send_enter:
                pyautogui.press('enter')
            success = True
        except Exception:
            pass

    return success

def get_system_clipboard() -> str:
    global last_clip_text
    return last_clip_text

def get_local_ips():
    ips = []
    try:
        s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        s.connect(('8.8.8.8', 80))
        ips.append(s.getsockname()[0])
        s.close()
    except Exception:
        pass

    try:
        out = subprocess.check_output(["ip", "-4", "addr", "show"], text=True, stderr=subprocess.DEVNULL)
        for line in out.splitlines():
            line = line.strip()
            if line.startswith("inet "):
                ip = line.split()[1].split("/")[0]
                if not ip.startswith("127.") and not ip.startswith("172.17.") and not ip.startswith("172.18.") and ip not in ips:
                    ips.append(ip)
    except Exception:
        pass

    if not ips:
        ips.append("127.0.0.1")
    return ips

def setup_adb_reverse(port: int):
    """If an Android device is plugged in via USB, forward the port automatically."""
    try:
        res = subprocess.run(["adb", "devices"], capture_output=True, text=True, timeout=2)
        if "device\n" in res.stdout or "\tdevice" in res.stdout:
            sub = subprocess.run(["adb", "reverse", f"tcp:{port}", f"tcp:{port}"], capture_output=True, text=True, timeout=2)
            if sub.returncode == 0:
                print(f"[✓] ADB Reverse activo: El celular puede conectarse directamente a http://localhost:{port} por USB!")
            else:
                print(f"[i] ADB reverse disponible pero falló: {sub.stderr.strip()}")
    except Exception:
        pass

def print_terminal_qr(url: str, title: str):
    if not HAVE_QRCODE:
        return
    try:
        qr = qrcode.QRCode(border=1)
        qr.add_data(url)
        qr.make(fit=True)
        print(f"\n┌──────────────────────────────────────────────┐")
        print(f"│ 📱 CÓDIGO QR PARA CELULAR: {title.center(18)}│")
        print(f"└──────────────────────────────────────────────┘")
        qr.print_ascii(invert=True)
        print(f"👉 URL de Conexión: {url}\n")
    except Exception as e:
        print(f"[!] Error al generar QR terminal: {e}")

HTML_PAGE = """<!DOCTYPE html>
<html lang="es">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <title>AutoClip Pro - PC Companion & Live Sync</title>
  <link rel="preconnect" href="https://fonts.googleapis.com">
  <link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
  <link href="https://fonts.googleapis.com/css2?family=Plus+Jakarta+Sans:wght@400;500;600;700;800&family=JetBrains+Mono:wght@400;500;600&display=swap" rel="stylesheet">
  <script src="https://cdn.jsdelivr.net/npm/qrcode@1.5.3/build/qrcode.min.js"></script>
  <style>
    :root {
      --bg: #0A0E17;
      --card-bg: #111827;
      --card-alt: #162032;
      --border: #1E293B;
      --border-focus: #38BDF8;
      --accent: #38BDF8;
      --accent-glow: rgba(56, 189, 248, 0.25);
      --accent-gradient: linear-gradient(135deg, #38BDF8 0%, #818CF8 50%, #C084FC 100%);
      --success: #10B981;
      --success-glow: rgba(16, 185, 129, 0.25);
      --warning: #F59E0B;
      --danger: #EF4444;
      --text: #F8FAFC;
      --text-muted: #94A3B8;
      --font-main: 'Plus Jakarta Sans', system-ui, -apple-system, sans-serif;
      --font-mono: 'JetBrains Mono', monospace;
    }
    * { box-sizing: border-box; margin: 0; padding: 0; }
    body {
      background: var(--bg);
      color: var(--text);
      font-family: var(--font-main);
      min-height: 100vh;
      display: flex;
      flex-direction: column;
      padding: 1.25rem;
      background-image: 
        radial-gradient(circle at 15% 15%, rgba(56, 189, 248, 0.08) 0%, transparent 40%),
        radial-gradient(circle at 85% 85%, rgba(129, 140, 248, 0.08) 0%, transparent 40%);
    }
    .container {
      max-width: 1050px;
      width: 100%;
      margin: 0 auto;
      display: flex;
      flex-direction: column;
      gap: 1.25rem;
      flex: 1;
    }
    header {
      display: flex;
      justify-content: space-between;
      align-items: center;
      padding: 1rem 1.5rem;
      background: var(--card-bg);
      border: 1px solid var(--border);
      border-radius: 20px;
      backdrop-filter: blur(12px);
      box-shadow: 0 10px 30px rgba(0, 0, 0, 0.4);
    }
    .logo-badge {
      display: flex;
      align-items: center;
      gap: 1rem;
    }
    .logo-icon {
      width: 44px;
      height: 44px;
      background: var(--accent-gradient);
      border-radius: 12px;
      display: flex;
      align-items: center;
      justify-content: center;
      font-size: 1.4rem;
      box-shadow: 0 4px 15px var(--accent-glow);
    }
    h1 {
      font-size: 1.35rem;
      font-weight: 800;
      letter-spacing: -0.03em;
      background: linear-gradient(to right, #F8FAFC, #94A3B8);
      -webkit-background-clip: text;
      -webkit-text-fill-color: transparent;
    }
    .header-actions {
      display: flex;
      align-items: center;
      gap: 0.75rem;
    }
    .status-pill {
      display: inline-flex;
      align-items: center;
      gap: 0.5rem;
      padding: 0.4rem 0.95rem;
      border-radius: 9999px;
      font-size: 0.82rem;
      font-weight: 700;
      background: rgba(16, 185, 129, 0.12);
      color: #34D399;
      border: 1px solid rgba(52, 211, 153, 0.3);
      transition: all 0.2s ease;
    }
    .status-dot {
      width: 8px;
      height: 8px;
      border-radius: 50%;
      background: #34D399;
      box-shadow: 0 0 10px #34D399;
    }
    .btn {
      background: var(--card-alt);
      border: 1px solid var(--border);
      color: var(--text);
      padding: 0.55rem 1.1rem;
      border-radius: 12px;
      font-family: var(--font-main);
      font-size: 0.85rem;
      font-weight: 600;
      cursor: pointer;
      display: inline-flex;
      align-items: center;
      gap: 0.5rem;
      transition: all 0.2s cubic-bezier(0.4, 0, 0.2, 1);
    }
    .btn:hover {
      background: var(--border);
      border-color: var(--accent);
      transform: translateY(-1px);
    }
    .btn-primary {
      background: linear-gradient(135deg, #0284C7, #2563EB);
      border-color: #38BDF8;
      color: white;
      box-shadow: 0 4px 14px rgba(37, 99, 235, 0.3);
    }
    .btn-primary:hover {
      background: linear-gradient(135deg, #0369A1, #1D4ED8);
      box-shadow: 0 6px 20px rgba(37, 99, 235, 0.45);
    }
    .btn-danger:hover {
      background: rgba(239, 68, 68, 0.15);
      border-color: rgba(239, 68, 68, 0.4);
      color: #F87171;
    }
    .grid-layout {
      display: grid;
      grid-template-columns: 1.1fr 0.9fr;
      gap: 1.25rem;
      flex: 1;
    }
    @media (max-width: 860px) {
      .grid-layout { grid-template-columns: 1fr; }
    }
    .panel-card {
      background: var(--card-bg);
      border: 1px solid var(--border);
      border-radius: 20px;
      padding: 1.25rem;
      display: flex;
      flex-direction: column;
      gap: 1rem;
      box-shadow: 0 10px 30px rgba(0, 0, 0, 0.3);
    }
    .panel-title-bar {
      display: flex;
      justify-content: space-between;
      align-items: center;
    }
    .panel-title {
      font-size: 0.95rem;
      font-weight: 700;
      display: flex;
      align-items: center;
      gap: 0.5rem;
      color: var(--text);
    }
    .target-selector-desc {
      font-size: 0.8rem;
      color: var(--text-muted);
      line-height: 1.4;
    }
    /* Target Fields Container */
    .target-fields-grid {
      display: flex;
      flex-direction: column;
      gap: 0.85rem;
      flex: 1;
    }
    .target-box {
      background: rgba(15, 23, 42, 0.6);
      border: 2px dashed var(--border);
      border-radius: 14px;
      padding: 0.85rem;
      cursor: pointer;
      transition: all 0.2s ease;
      position: relative;
    }
    .target-box:hover {
      border-color: rgba(56, 189, 248, 0.5);
      background: rgba(56, 189, 248, 0.03);
    }
    .target-box.active-target {
      border: 2px solid var(--accent);
      background: rgba(56, 189, 248, 0.08);
      box-shadow: 0 0 20px var(--accent-glow);
    }
    .target-header {
      display: flex;
      justify-content: space-between;
      align-items: center;
      margin-bottom: 0.4rem;
    }
    .target-name {
      font-size: 0.82rem;
      font-weight: 700;
      color: var(--accent);
      display: flex;
      align-items: center;
      gap: 0.4rem;
    }
    .target-indicator {
      font-size: 0.72rem;
      font-weight: 600;
      padding: 0.15rem 0.5rem;
      border-radius: 6px;
      background: rgba(56, 189, 248, 0.15);
      color: var(--accent);
    }
    .target-input {
      width: 100%;
      background: rgba(11, 15, 23, 0.7);
      border: 1px solid var(--border);
      border-radius: 8px;
      color: var(--text);
      font-family: var(--font-mono);
      font-size: 0.9rem;
      padding: 0.6rem 0.75rem;
      outline: none;
      transition: border-color 0.2s ease;
    }
    .target-textarea {
      min-height: 80px;
      resize: vertical;
    }
    .target-input:focus {
      border-color: var(--accent);
    }
    /* Main Editor */
    textarea#editor {
      width: 100%;
      flex: 1;
      min-height: 220px;
      background: rgba(11, 15, 23, 0.8);
      border: 1px solid var(--border);
      border-radius: 14px;
      color: var(--text);
      font-family: var(--font-mono);
      font-size: 0.95rem;
      line-height: 1.6;
      padding: 1rem;
      resize: vertical;
      outline: none;
      transition: border-color 0.2s ease;
    }
    textarea#editor:focus {
      border-color: var(--accent);
    }
    .history-list {
      display: flex;
      flex-direction: column;
      gap: 0.5rem;
      max-height: 150px;
      overflow-y: auto;
    }
    .history-item {
      display: flex;
      justify-content: space-between;
      align-items: center;
      background: rgba(15, 23, 42, 0.5);
      padding: 0.5rem 0.75rem;
      border-radius: 8px;
      border: 1px solid transparent;
      cursor: pointer;
      font-family: var(--font-mono);
      font-size: 0.82rem;
      transition: all 0.15s ease;
    }
    .history-item:hover {
      border-color: var(--accent);
      background: rgba(56, 189, 248, 0.05);
    }
    .history-text {
      white-space: nowrap;
      overflow: hidden;
      text-overflow: ellipsis;
      max-width: 75%;
    }
    .history-time {
      font-size: 0.75rem;
      color: var(--text-muted);
    }
    /* Modal QR */
    .modal-backdrop {
      position: fixed;
      inset: 0;
      background: rgba(0, 0, 0, 0.75);
      backdrop-filter: blur(8px);
      display: none;
      align-items: center;
      justify-content: center;
      z-index: 1000;
      padding: 1rem;
    }
    .modal-backdrop.open {
      display: flex;
    }
    .modal-content {
      background: var(--card-bg);
      border: 1px solid var(--border);
      border-radius: 24px;
      padding: 2rem;
      max-width: 420px;
      width: 100%;
      text-align: center;
      display: flex;
      flex-direction: column;
      align-items: center;
      gap: 1.25rem;
      box-shadow: 0 25px 50px -12px rgba(0, 0, 0, 0.6);
      animation: popIn 0.25s cubic-bezier(0.16, 1, 0.3, 1);
    }
    @keyframes popIn {
      0% { transform: scale(0.92); opacity: 0; }
      100% { transform: scale(1); opacity: 1; }
    }
    .qr-canvas-container {
      background: white;
      padding: 1rem;
      border-radius: 18px;
      display: flex;
      align-items: center;
      justify-content: center;
      box-shadow: 0 10px 25px rgba(0,0,0,0.3);
    }
    .ip-selector {
      width: 100%;
      background: var(--card-alt);
      border: 1px solid var(--border);
      color: var(--text);
      padding: 0.6rem 0.9rem;
      border-radius: 10px;
      font-family: var(--font-main);
      font-size: 0.85rem;
      outline: none;
    }
  </style>
</head>
<body>
  <div class="container">
    <header>
      <div class="logo-badge">
        <div class="logo-icon">⚡</div>
        <div>
          <h1>AutoClip Pro</h1>
          <p style="font-size: 0.75rem; color: var(--text-muted);">Sincronizador & Escritura En Vivo en PC</p>
        </div>
      </div>
      <div class="header-actions">
        <button class="btn btn-primary" id="btnOpenQr">
          <span>📱 Conectar Celular (QR)</span>
        </button>
        <div id="statusPill" class="status-pill">
          <div id="statusDot" class="status-dot"></div>
          <span id="statusText">PC Conectada</span>
        </div>
      </div>
    </header>

    <div class="grid-layout">
      <!-- Left Panel: Main Live Editor & History -->
      <div class="panel-card">
        <div class="panel-title-bar">
          <div class="panel-title">
            <span>📋 Portapapeles & Editor Global</span>
          </div>
          <span id="charCount" style="font-size: 0.8rem; color: var(--text-muted);">0 caracteres</span>
        </div>

        <textarea id="editor" placeholder="Escribe aquí desde el celular o desde la PC... todo se sincroniza en tiempo real." autofocus></textarea>

        <div style="display: flex; justify-content: space-between; align-items: center;">
          <button class="btn btn-danger" id="btnClear">🧹 Limpiar</button>
          <span id="lastSyncTime" style="font-size: 0.78rem; color: var(--text-muted);">Listo</span>
        </div>

        <div style="border-top: 1px solid var(--border); padding-top: 0.75rem; margin-top: 0.25rem;">
          <div class="panel-title" style="margin-bottom: 0.5rem; font-size: 0.85rem;">Historial Reciente</div>
          <div class="history-list" id="historyList">
            <div style="font-size: 0.8rem; color: var(--text-muted); padding: 0.25rem 0;">Aún no hay textos enviados.</div>
          </div>
        </div>
      </div>

      <!-- Right Panel: Live Target Field Selector -->
      <div class="panel-card">
        <div class="panel-title-bar">
          <div class="panel-title">
            <span>🎯 Modo En Vivo: Selector de Campos</span>
          </div>
          <span style="font-size: 0.75rem; color: var(--accent); font-weight: 700;">🟢 EN VIVO</span>
        </div>

        <p class="target-selector-desc">
          Haz clic en cualquier campo para fijarlo como <strong>objetivo</strong>. Lo que escribas en tu celular en <em>Modo En Vivo</em> se escribirá automáticamente en el campo seleccionado:
        </p>

        <div class="target-fields-grid">
          <!-- Target 1: Quick Search / Chat -->
          <div class="target-box active-target" data-target="chat" onclick="selectTarget('chat')">
            <div class="target-header">
              <span class="target-name">🔍 Búsqueda Rápida / Chat</span>
              <span class="target-indicator" id="ind-chat">🎯 Activo</span>
            </div>
            <input type="text" class="target-input" id="field-chat" placeholder="Escribe aquí en vivo..." />
          </div>

          <!-- Target 2: Notes / Document Area -->
          <div class="target-box" data-target="notes" onclick="selectTarget('notes')">
            <div class="target-header">
              <span class="target-name">📝 Bloc de Notas & Documento</span>
              <span class="target-indicator" id="ind-notes" style="display:none;">🎯 Activo</span>
            </div>
            <textarea class="target-input target-textarea" id="field-notes" placeholder="Texto largo o notas dictadas desde el celular..."></textarea>
          </div>

          <!-- Target 3: Terminal / Code snippet -->
          <div class="target-box" data-target="code" onclick="selectTarget('code')">
            <div class="target-header">
              <span class="target-name">💻 Terminal / Comandos</span>
              <span class="target-indicator" id="ind-code" style="display:none;">🎯 Activo</span>
            </div>
            <input type="text" class="target-input" id="field-code" style="font-family: var(--font-mono); color: #34D399;" placeholder="$ git commit -m '...'" />
          </div>
        </div>

        <div style="background: rgba(56, 189, 248, 0.08); border: 1px solid rgba(56, 189, 248, 0.2); border-radius: 12px; padding: 0.75rem; font-size: 0.78rem; color: var(--text-muted); line-height: 1.4;">
          💡 <strong>Tip PC Global:</strong> Si cambias de ventana a Word, Discord o tu navegador, el <em>Modo En Vivo</em> escribirá directamente en el programa que tengas abierto en pantalla.
        </div>
      </div>
    </div>
  </div>

  <!-- QR Modal -->
  <div class="modal-backdrop" id="qrModal">
    <div class="modal-content">
      <div style="display: flex; align-items: center; justify-content: space-between; width: 100%;">
        <h3 style="font-size: 1.15rem; font-weight: 800;">📱 Sincronizar Celular</h3>
        <button class="btn" style="padding: 0.3rem 0.6rem;" onclick="closeQrModal()">✕</button>
      </div>

      <p style="font-size: 0.82rem; color: var(--text-muted);">
        Abre <strong>AutoClip</strong> en tu celular y pulsa el botón <strong>Escanear QR 📷</strong>.
      </p>

      <div class="qr-canvas-container">
        <canvas id="qrCanvas"></canvas>
      </div>

      <div style="width: 100%; text-align: left;">
        <label style="font-size: 0.75rem; color: var(--text-muted); margin-bottom: 0.25rem; display: block;">Dirección IP de esta PC:</label>
        <select class="ip-selector" id="ipSelect" onchange="renderCurrentQr()">
          <!-- Populated by JS -->
        </select>
      </div>

      <button class="btn btn-primary" style="width: 100%; justify-content: center;" onclick="closeQrModal()">Listo</button>
    </div>
  </div>

  <script>
    const editor = document.getElementById('editor');
    const charCount = document.getElementById('charCount');
    const btnClear = document.getElementById('btnClear');
    const lastSyncTime = document.getElementById('lastSyncTime');
    const historyList = document.getElementById('historyList');
    const btnOpenQr = document.getElementById('btnOpenQr');
    const qrModal = document.getElementById('qrModal');
    const ipSelect = document.getElementById('ipSelect');
    const qrCanvas = document.getElementById('qrCanvas');

    let activeTargetId = 'chat';
    let localIps = [];
    let serverPort = 8765;
    let isTypingLocal = false;

    function selectTarget(targetId) {
      activeTargetId = targetId;
      document.querySelectorAll('.target-box').forEach(box => {
        const isCurrent = box.getAttribute('data-target') === targetId;
        box.classList.toggle('active-target', isCurrent);
        const ind = document.getElementById(`ind-${box.getAttribute('data-target')}`);
        if (ind) ind.style.display = isCurrent ? 'inline-block' : 'none';
      });
      const input = document.getElementById(`field-${targetId}`);
      if (input) input.focus();
    }

    function updateCounts(txt) {
      const chars = txt.length;
      const words = txt.trim() ? txt.trim().split(/\\s+/).length : 0;
      charCount.textContent = `${chars} caracteres • ${words} palabras`;
    }

    async function sendToPcClipboard(text) {
      try {
        const res = await fetch('/api/clip', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ text })
        });
        if (res.ok) {
          const now = new Date().toLocaleTimeString();
          lastSyncTime.textContent = `Sincronizado a las ${now}`;
          loadHistory();
        }
      } catch (err) {}
    }

    editor.addEventListener('input', () => {
      isTypingLocal = true;
      const text = editor.value;
      updateCounts(text);
      sendToPcClipboard(text);
      setTimeout(() => { isTypingLocal = false; }, 300);
    });

    btnClear.addEventListener('click', () => {
      editor.value = '';
      updateCounts('');
      sendToPcClipboard('');
      const targetInput = document.getElementById(`field-${activeTargetId}`);
      if (targetInput) targetInput.value = '';
      editor.focus();
    });

    // Server-Sent Events (Live streaming from mobile app)
    function setupLiveEvents() {
      const evtSource = new EventSource('/api/stream');
      
      evtSource.addEventListener('clip', (e) => {
        if (isTypingLocal) return;
        const data = JSON.parse(e.data);
        if (editor.value !== data.text) {
          editor.value = data.text;
          updateCounts(data.text);
          lastSyncTime.textContent = `Recibido desde celular a las ${data.time}`;
          loadHistory();
        }
      });

      evtSource.addEventListener('type', (e) => {
        const data = JSON.parse(e.data);
        const targetInput = document.getElementById(`field-${activeTargetId}`);
        if (targetInput) {
          targetInput.value = data.text;
          targetInput.classList.add('active-target');
          setTimeout(() => targetInput.classList.remove('active-target'), 200);
        }
      });
    }

    async function fetchServerStatus() {
      try {
        const res = await fetch('/api/status');
        if (res.ok) {
          const data = await res.json();
          localIps = data.ips || ['127.0.0.1'];
          serverPort = data.port || 8765;
          populateIpSelect();
        }
      } catch (e) {}
    }

    function populateIpSelect() {
      ipSelect.innerHTML = localIps.map(ip => `
        <option value="${ip}">${ip} (${ip.startsWith('127.') ? 'USB/Local' : 'WiFi'})</option>
      `).join('');
      renderCurrentQr();
    }

    function renderCurrentQr() {
      const selectedIp = ipSelect.value || '127.0.0.1';
      const payload = JSON.stringify({
        app: "AutoClip",
        host: selectedIp,
        port: serverPort,
        url: `http://${selectedIp}:${serverPort}`
      });
      QRCode.toCanvas(qrCanvas, payload, {
        width: 220,
        margin: 1,
        color: { dark: '#0F172A', light: '#FFFFFF' }
      });
    }

    btnOpenQr.addEventListener('click', () => {
      qrModal.classList.add('open');
      renderCurrentQr();
    });

    function closeQrModal() {
      qrModal.classList.remove('open');
    }

    qrModal.addEventListener('click', (e) => {
      if (e.target === qrModal) closeQrModal();
    });

    async function loadHistory() {
      try {
        const res = await fetch('/api/history');
        if (res.ok) {
          const data = await res.json();
          if (data.history && data.history.length > 0) {
            historyList.innerHTML = data.history.map(item => `
              <div class="history-item" onclick="setEditorContent(${JSON.stringify(item.text)})">
                <span class="history-text">${escapeHtml(item.text || '[Vacío]')}</span>
                <span class="history-time">${item.time}</span>
              </div>
            `).join('');
          }
        }
      } catch (e) {}
    }

    window.setEditorContent = (txt) => {
      editor.value = txt;
      updateCounts(txt);
      sendToPcClipboard(txt);
      editor.focus();
    };

    function escapeHtml(str) {
      return str.replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;").replace(/"/g, "&quot;");
    }

    fetchServerStatus();
    loadHistory();
    setupLiveEvents();
  </script>
</body>
</html>
"""

class AutoClipHTTPHandler(BaseHTTPRequestHandler):
    def _send_cors_headers(self):
        self.send_header("Access-Control-Allow-Origin", "*")
        self.send_header("Access-Control-Allow-Methods", "GET, POST, OPTIONS")
        self.send_header("Access-Control-Allow-Headers", "Content-Type, Authorization")

    def do_OPTIONS(self):
        self.send_response(200)
        self._send_cors_headers()
        self.end_headers()

    def do_GET(self):
        parsed = urlparse(self.path)
        path = parsed.path

        if path == "/api/status":
            self.send_response(200)
            self._send_cors_headers()
            self.send_header("Content-Type", "application/json; charset=utf-8")
            self.end_headers()
            data = {
                "status": "online",
                "service": "AutoClip Pro PC Companion",
                "hostname": socket.gethostname(),
                "ips": get_local_ips(),
                "port": PORT,
                "direct_typing": direct_typing_enabled,
                "timestamp": time.time()
            }
            self.wfile.write(json.dumps(data).encode("utf-8"))

        elif path == "/api/clip":
            self.send_response(200)
            self._send_cors_headers()
            self.send_header("Content-Type", "application/json; charset=utf-8")
            self.end_headers()
            data = {
                "text": get_system_clipboard(),
                "time": last_clip_time,
                "length": len(last_clip_text)
            }
            self.wfile.write(json.dumps(data).encode("utf-8"))

        elif path == "/api/history":
            self.send_response(200)
            self._send_cors_headers()
            self.send_header("Content-Type", "application/json; charset=utf-8")
            self.end_headers()
            data = {
                "history": clip_history
            }
            self.wfile.write(json.dumps(data).encode("utf-8"))

        elif path == "/api/stream":
            # Server-Sent Events stream for live typing & clipboard updates
            self.send_response(200)
            self._send_cors_headers()
            self.send_header("Content-Type", "text/event-stream")
            self.send_header("Cache-Control", "no-cache")
            self.send_header("Connection", "keep-alive")
            self.end_headers()
            with sse_lock:
                sse_subscribers.append(self)
            try:
                # Keep alive loop
                while True:
                    time.sleep(15)
                    self.wfile.write(b": keepalive\n\n")
                    self.wfile.flush()
            except Exception:
                with sse_lock:
                    if self in sse_subscribers:
                        sse_subscribers.remove(self)

        elif path == "/" or path == "/index.html":
            self.send_response(200)
            self._send_cors_headers()
            self.send_header("Content-Type", "text/html; charset=utf-8")
            self.end_headers()
            self.wfile.write(HTML_PAGE.encode("utf-8"))

        else:
            self.send_response(404)
            self.end_headers()
            self.wfile.write(b"Not found")

    def do_POST(self):
        parsed = urlparse(self.path)
        path = parsed.path

        if path == "/api/clip":
            content_length = int(self.headers.get("Content-Length", 0))
            body = self.rfile.read(content_length)
            try:
                data = json.loads(body.decode("utf-8"))
                text = data.get("text", "")
            except Exception:
                text = body.decode("utf-8", errors="ignore")

            ok = set_system_clipboard(text)
            self.send_response(200)
            self._send_cors_headers()
            self.send_header("Content-Type", "application/json; charset=utf-8")
            self.end_headers()
            resp = {
                "status": "ok" if ok else "warning",
                "received_length": len(text),
                "timestamp": time.time()
            }
            self.wfile.write(json.dumps(resp).encode("utf-8"))

        elif path == "/api/type":
            # Live direct typing endpoint
            content_length = int(self.headers.get("Content-Length", 0))
            body = self.rfile.read(content_length)
            try:
                data = json.loads(body.decode("utf-8"))
                text = data.get("text", "")
                send_enter = data.get("enter", False)
            except Exception:
                text = body.decode("utf-8", errors="ignore")
                send_enter = False

            ok = simulate_keyboard_typing(text, send_enter=send_enter)
            self.send_response(200)
            self._send_cors_headers()
            self.send_header("Content-Type", "application/json; charset=utf-8")
            self.end_headers()
            resp = {
                "status": "ok" if ok else "warning",
                "typed_length": len(text),
                "timestamp": time.time()
            }
            self.wfile.write(json.dumps(resp).encode("utf-8"))

        else:
            self.send_response(404)
            self.end_headers()
            self.wfile.write(b"Not found")

    def log_message(self, format, *args):
        pass

def main():
    init_clipboard()
    setup_adb_reverse(PORT)
    
    ips = get_local_ips()
    primary_ip = ips[0] if ips else "127.0.0.1"

    server = ThreadingHTTPServer((HOST, PORT), AutoClipHTTPHandler)
    server_thread = threading.Thread(target=server.serve_forever, daemon=True)
    server_thread.start()

    print("=" * 68)
    print(" 🚀 AutoClip Pro Server - Sincronizador & Escritura En Vivo")
    print("=" * 68)
    print(f" [*] Estado: Activo y escuchando en el puerto {PORT}")
    print(f" [*] Conexión WiFi (celular en la misma red):")
    for ip in ips:
        print(f"     👉 http://{ip}:{PORT}")
    print(f" [*] Conexión USB (con cable / ADB):")
    print(f"     👉 http://localhost:{PORT}")
    print(f" [*] Panel web de prueba & selector de campos:")
    print(f"     👉 http://localhost:{PORT}")
    print("=" * 68)

    # Print ASCII QR code in terminal for immediate scan
    for ip in ips:
        if not ip.startswith("127."):
            print_terminal_qr(f"http://{ip}:{PORT}", f"WiFi ({ip})")
            break
    else:
        print_terminal_qr(f"http://127.0.0.1:{PORT}", "USB Localhost")

    print(" [✓] Cada vez que escribas o pegues en la app Android:")
    print("     1. Se sincroniza al portapapeles de la PC.")
    print("     2. En Modo En Vivo, escribe directo en tu ventana activa / campo.")
    print("     Presiona Ctrl + C para detener el servidor.")
    print("=" * 68 + "\n")

    global tk_root
    if tk_root is not None:
        try:
            tk_root.mainloop()
        except KeyboardInterrupt:
            pass
    else:
        try:
            while True:
                time.sleep(1)
        except KeyboardInterrupt:
            pass

    print("\n[!] Deteniendo AutoClip Server...")
    server.shutdown()
    print("[✓] Servidor detenido.")

if __name__ == "__main__":
    main()
