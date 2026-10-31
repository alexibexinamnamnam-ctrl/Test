# obs-multistream-chat

OBS-Plugin (C++), das zwei Dinge macht:

1. **Multistream-Dock**: bis zu 4 zusätzliche RTMP-Ziele, die mit "Stream starten" automatisch mitlaufen. Sie nutzen den Encoder des Haupt-Streams mit, es wird also nicht mehrfach encodiert.
2. **Chat-Overlay**: Twitch- und YouTube-Chat zusammen in einer Browser-Quelle, Hintergrund durchsichtig.

Benötigt OBS 30 oder neuer (Windows).

## Bauen (empfohlener Weg: obs-plugintemplate)

1. Auf GitHub das Repository `obsproject/obs-plugintemplate` per "Use this template" kopieren.
2. In `CMakeLists.txt` des Templates beide Optionen einschalten:
   `set(ENABLE_FRONTEND_API ON)` und `set(ENABLE_QT ON)`.
3. `src/plugin-main.c` löschen, stattdessen `src/plugin-main.cpp` aus diesem Ordner hineinkopieren und in `CMakeLists.txt` bei `target_sources` anpassen.
4. `data/chat.html` aus diesem Ordner nach `data/` kopieren (dort lag im Template die `locale`-Datei).
5. Name des Plugins in `buildspec.json` auf `obs-multistream-chat` setzen.
6. Auf GitHub unter **Actions** den Build starten. Das Template baut auf GitHub für Windows automatisch. Am Ende liegt dort eine fertige Zip/Installer-Datei zum Herunterladen.

## Benutzung

1. Nach dem Installieren OBS starten, dann unter **Ansicht → Andockbare Fenster** den Eintrag **Multistream** einschalten.
2. Zusatzziele: Haken setzen, Server und Key eintragen.
3. Chat: Twitch-Kanalname eintragen. Für YouTube braucht es einen API-Key (Google Cloud Console, "YouTube Data API v3") und die Video-ID des Livestreams (der Teil nach `v=` in der Stream-URL).
4. Auf **Chat-Overlay erstellen** klicken. In der aktuellen Szene erscheint die Quelle "Multichat", die du verschieben und skalieren kannst.

## Hinweise

- Die Keys werden im Klartext in `settings.json` im OBS-Konfigurordner des Plugins gespeichert.
- Twitch-Chat funktioniert ohne Login. YouTube-Chat wird über die API alle paar Sekunden abgefragt und verbraucht Tageskontingent.
- Dieser Code wurde nicht kompiliert und nicht in OBS getestet.
