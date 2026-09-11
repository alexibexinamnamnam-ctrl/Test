# Ollama AI Play – Fabric-Mod für Minecraft 1.20.1

Diese Mod lässt eine lokal laufende **Ollama**-KI deinen Minecraft-Charakter steuern.
Sie läuft im Spiel (Client-seitig) und fragt in regelmäßigen Abständen die
Ollama-API nach der nächsten Aktion (bewegen, umsehen, springen, angreifen,
abbauen …) und führt diese aus.

## Voraussetzungen

1. **Java 17** (JDK) installiert.
2. **Ollama** installiert und gestartet: https://ollama.com
   ```bash
   ollama serve
   ollama pull llama3.1
   ```
   Die Mod erwartet Ollama unter `http://localhost:11434` (Standard).
3. **Fabric Loader** + **Fabric API** für Minecraft 1.20.1 im normalen
   Minecraft-Launcher installiert (die Mod braucht das Fabric-API-Mod
   zusätzlich im `mods`-Ordner).

## Bauen OHNE Terminal (empfohlen) – über GitHub Actions

Du brauchst dafür nur einen (kostenlosen) GitHub-Account, keine Kommandozeile:

1. Gehe zu https://github.com und logge dich ein (Account erstellen, falls
   nötig – kostenlos).
2. Klicke oben rechts auf **"+"** → **"New repository"**. Name z. B.
   `ollama-ai-play-mod`, auf "Public" oder "Private" stellen, **"Create
   repository"** klicken.
3. Auf der neuen, leeren Repo-Seite auf **"uploading an existing file"**
   klicken.
4. Den kompletten Inhalt des entpackten `ollama-ai-play-mod`-Ordners
   (alle Dateien und Unterordner, inkl. `.github`) per Drag & Drop dort
   hineinziehen. Achtung: Du musst den **Inhalt** des Ordners hochladen,
   nicht den Ordner selbst als Zip.
5. Unten auf **"Commit changes"** klicken.
6. Oben im Repo auf den Reiter **"Actions"** klicken. Dort startet
   automatisch ein Workflow namens **"Build Mod"** (dauert ca. 2–3 Minuten).
7. Ist der Workflow fertig (grüner Haken), klickst du ihn an und scrollst
   zu **"Artifacts"** ganz unten. Dort liegt **`ollama-ai-play-mod`** zum
   Herunterladen – das ist ein Zip mit der fertigen `.jar`-Datei drin.
8. Zip entpacken → die `.jar` in deinen `.minecraft/mods`-Ordner legen.

Kein einziger Terminal-Befehl nötig – GitHub baut die Mod komplett in der
Cloud und du lädst am Ende nur die fertige Datei herunter.

## Bauen mit Terminal (Alternative, falls du lieber lokal baust)

```bash
cd ollama-ai-play-mod
./gradlew build
```

Der erste Build lädt Minecraft-Mappings/Loader über das Netzwerk herunter
(braucht also Internetzugang). Das fertige Jar liegt danach unter:

```
build/libs/ollama-ai-play-1.0.0.jar
```

Falls `gradlew` nicht ausführbar ist: `chmod +x gradlew`. Unter Windows
`gradlew.bat build` verwenden. Falls der Gradle-Wrapper-JAR fehlt, einmalig
`gradle wrapper` mit lokal installiertem Gradle ausführen, oder das Projekt
in IntelliJ IDEA mit dem Fabric-Loom-Plugin öffnen.

## Installation

1. Fertiges Jar aus `build/libs/` in den `.minecraft/mods`-Ordner kopieren.
2. Sicherstellen, dass auch **fabric-api-*.jar** dort liegt.
3. Minecraft mit dem Fabric-Profil starten.

## Benutzung im Spiel

Im Chat/Befehlszeile eingeben:

| Befehl | Wirkung |
|---|---|
| `/aiplay start` | KI übernimmt die Steuerung |
| `/aiplay stop` | KI wird gestoppt, Tasten werden losgelassen |
| `/aiplay model <name>` | Ollama-Modell wechseln (Standard: `llama3.1`) |
| `/aiplay interval <ticks>` | Wie oft (in Ticks, 20 = 1s) neu entschieden wird (Standard: 30) |

## Wie es funktioniert

- `GameStateCollector` baut eine kurze Textbeschreibung des Zustands
  (Position, Leben, Hunger, Tageszeit, Block im Fadenkreuz, Mobs in der Nähe …).
- `OllamaClient` schickt diese Beschreibung als User-Prompt zusammen mit
  einem System-Prompt an `POST /api/chat` von Ollama und erwartet eine
  Antwort im Format `{"action": "MOVE_FORWARD", "reason": "..."}`.
- `AiController` läuft im Client-Tick-Event, wartet nicht-blockierend auf
  die Ollama-Antwort (HTTP läuft asynchron im Hintergrund) und reicht die
  gewählte Aktion an `ActionExecutor` weiter.
- `ActionExecutor` setzt echte Bewegungstasten (`player.input.pressForward`
  etc.), dreht den Blick (Yaw/Pitch) oder löst Angriff/Abbau/Platzieren aus.

## Bekannte Grenzen (bewusst einfach gehalten)

- Kein Pathfinding – die KI entscheidet Schritt für Schritt anhand des
  aktuellen Fadenkreuz-Ziels, nicht anhand einer vollständigen Karte.
- Blockabbau ist vereinfacht (ein `attackBlock`-Aufruf pro Zyklus statt
  exaktem Timing für Abbaugeschwindigkeit).
- Kein Inventar-Management/Crafting – nur Bewegung, Umsehen, Angriff,
  Abbau, Platzieren.
- Bei jedem Zyklus wird ein HTTP-Call gemacht; bei sehr kleinem Intervall
  kann das Anfragen stauen (wird durch `waitingForResponse` verhindert,
  d.h. die KI überspringt Ticks, bis die vorherige Antwort da ist).

Diese Grenzen lassen sich erweitern: z. B. mehr Kontext im Prompt
(vollständiges Inventar, Blockscan in mehreren Richtungen), mehr Aktionen,
oder Bild-Kontext, falls ein multimodales Ollama-Modell verwendet wird.
