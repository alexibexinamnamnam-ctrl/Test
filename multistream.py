"""
OBS Multistream-Script
Streamt zusätzlich zum normalen OBS-Stream an bis zu 4 weitere Ziele.
Nutzt den Encoder des Haupt-Streams mit -> kein zusätzlicher CPU-Verbrauch.

Installation:
 1. OBS: Werkzeuge -> Skripte -> "+" -> diese Datei wählen
 2. Python muss in OBS eingestellt sein (Skripte -> Python-Einstellungen)
 3. Server + Key der Zusatzziele eintragen, Haken setzen
 4. Ganz normal auf "Stream starten" klicken - die Zusatzziele starten automatisch
"""
import obspython as obs

SLOTS = 4
settings_ref = None
outputs = {}  # slot -> output
services = {}  # slot -> service (muss am Leben bleiben, solange der Output läuft)


def script_description():
    return "Multistream: sendet den Haupt-Stream zusätzlich an weitere RTMP-Ziele (gleicher Encoder)."


def script_properties():
    props = obs.obs_properties_create()
    for i in range(1, SLOTS + 1):
        obs.obs_properties_add_bool(props, f"enable{i}", f"Ziel {i} aktiv")
        obs.obs_properties_add_text(props, f"server{i}", f"Ziel {i} Server (rtmp://...)", obs.OBS_TEXT_DEFAULT)
        obs.obs_properties_add_text(props, f"key{i}", f"Ziel {i} Stream-Key", obs.OBS_TEXT_PASSWORD)
    return props


def script_update(settings):
    global settings_ref
    settings_ref = settings


def start_targets():
    main = obs.obs_frontend_get_streaming_output()
    if main is None:
        return
    venc = obs.obs_output_get_video_encoder(main)
    aenc = obs.obs_output_get_audio_encoder(main, 0)
    obs.obs_output_release(main)
    if venc is None or aenc is None:
        print("[Multistream] Encoder des Haupt-Streams nicht gefunden")
        return

    for i in range(1, SLOTS + 1):
        if not obs.obs_data_get_bool(settings_ref, f"enable{i}"):
            continue
        server = obs.obs_data_get_string(settings_ref, f"server{i}").strip()
        key = obs.obs_data_get_string(settings_ref, f"key{i}").strip()
        if not server:
            continue

        sdata = obs.obs_data_create()
        obs.obs_data_set_string(sdata, "server", server)
        obs.obs_data_set_string(sdata, "key", key)
        service = obs.obs_service_create("rtmp_custom", f"multistream_svc{i}", sdata, None)
        obs.obs_data_release(sdata)

        out = obs.obs_output_create("rtmp_output", f"multistream_out{i}", None, None)
        obs.obs_output_set_service(out, service)
        obs.obs_output_set_video_encoder(out, venc)
        obs.obs_output_set_audio_encoder(out, aenc, 0)

        if obs.obs_output_start(out):
            outputs[i] = out
            services[i] = service
            print(f"[Multistream] Ziel {i} gestartet")
        else:
            print(f"[Multistream] Ziel {i} Fehler: {obs.obs_output_get_last_error(out)}")
            obs.obs_output_release(out)
            obs.obs_service_release(service)


def stop_targets():
    for i, out in list(outputs.items()):
        obs.obs_output_stop(out)
        obs.obs_output_release(out)
        if i in services:
            obs.obs_service_release(services.pop(i))
        print(f"[Multistream] Ziel {i} gestoppt")
    outputs.clear()


def on_event(event):
    if event == obs.OBS_FRONTEND_EVENT_STREAMING_STARTED:
        start_targets()
    elif event == obs.OBS_FRONTEND_EVENT_STREAMING_STOPPING:
        stop_targets()


def script_load(settings):
    obs.obs_frontend_add_event_callback(on_event)


def script_unload():
    stop_targets()
