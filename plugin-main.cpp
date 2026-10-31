// obs-multistream-chat
// - Dock "Multistream": zusätzliche RTMP-Ziele (nutzt den Encoder des Haupt-Streams mit)
// - Button "Chat-Overlay erstellen": legt eine Browser-Quelle mit Twitch- + YouTube-Chat
//   auf transparentem Hintergrund in der aktuellen Szene an
// Benötigt OBS 30 oder neuer (obs_frontend_add_dock_by_id).

#include <obs-module.h>
#include <obs-frontend-api.h>
#include <util/platform.h>

#include <QCheckBox>
#include <QFileInfo>
#include <QGridLayout>
#include <QGroupBox>
#include <QLabel>
#include <QLineEdit>
#include <QPushButton>
#include <QString>
#include <QUrl>
#include <QUrlQuery>
#include <QVBoxLayout>
#include <QWidget>

#include <string>

OBS_DECLARE_MODULE()
OBS_MODULE_USE_DEFAULT_LOCALE("obs-multistream-chat", "en-US")

static constexpr int SLOTS = 4;

struct State {
	QLineEdit *twitch = nullptr;
	QLineEdit *ytKey = nullptr;
	QLineEdit *ytVideo = nullptr;
	QCheckBox *enable[SLOTS] = {};
	QLineEdit *server[SLOTS] = {};
	QLineEdit *key[SLOTS] = {};
	obs_output_t *outs[SLOTS] = {};
	obs_service_t *svcs[SLOTS] = {};
};
static State *g = nullptr;

// ---------------------------------------------------------------- Einstellungen

static std::string settings_path()
{
	char *dir = obs_module_config_path("");
	if (dir) {
		os_mkdirs(dir);
		bfree(dir);
	}
	char *p = obs_module_config_path("settings.json");
	std::string s = p ? p : "";
	bfree(p);
	return s;
}

static void save_settings()
{
	if (!g)
		return;
	obs_data_t *d = obs_data_create();
	obs_data_set_string(d, "twitch", g->twitch->text().toUtf8().constData());
	obs_data_set_string(d, "ytkey", g->ytKey->text().toUtf8().constData());
	obs_data_set_string(d, "ytvideo", g->ytVideo->text().toUtf8().constData());
	for (int i = 0; i < SLOTS; i++) {
		std::string n = std::to_string(i);
		obs_data_set_bool(d, ("on" + n).c_str(), g->enable[i]->isChecked());
		obs_data_set_string(d, ("server" + n).c_str(), g->server[i]->text().toUtf8().constData());
		obs_data_set_string(d, ("key" + n).c_str(), g->key[i]->text().toUtf8().constData());
	}
	obs_data_save_json_safe(d, settings_path().c_str(), "tmp", "bak");
	obs_data_release(d);
}

static void load_settings()
{
	obs_data_t *d = obs_data_create_from_json_file_safe(settings_path().c_str(), "bak");
	if (!d)
		return;
	g->twitch->setText(obs_data_get_string(d, "twitch"));
	g->ytKey->setText(obs_data_get_string(d, "ytkey"));
	g->ytVideo->setText(obs_data_get_string(d, "ytvideo"));
	for (int i = 0; i < SLOTS; i++) {
		std::string n = std::to_string(i);
		g->enable[i]->setChecked(obs_data_get_bool(d, ("on" + n).c_str()));
		g->server[i]->setText(obs_data_get_string(d, ("server" + n).c_str()));
		g->key[i]->setText(obs_data_get_string(d, ("key" + n).c_str()));
	}
	obs_data_release(d);
}

// ---------------------------------------------------------------- Multistream

static void stop_targets()
{
	if (!g)
		return;
	for (int i = 0; i < SLOTS; i++) {
		if (g->outs[i]) {
			obs_output_stop(g->outs[i]);
			obs_output_release(g->outs[i]);
			g->outs[i] = nullptr;
		}
		if (g->svcs[i]) {
			obs_service_release(g->svcs[i]);
			g->svcs[i] = nullptr;
		}
	}
}

static void start_targets()
{
	if (!g)
		return;
	obs_output_t *main = obs_frontend_get_streaming_output();
	if (!main)
		return;
	obs_encoder_t *venc = obs_output_get_video_encoder(main);
	obs_encoder_t *aenc = obs_output_get_audio_encoder(main, 0);
	if (!venc || !aenc) {
		blog(LOG_WARNING, "[multistream-chat] Encoder des Haupt-Streams nicht gefunden");
		obs_output_release(main);
		return;
	}

	for (int i = 0; i < SLOTS; i++) {
		if (!g->enable[i]->isChecked())
			continue;
		std::string server = g->server[i]->text().trimmed().toStdString();
		std::string key = g->key[i]->text().trimmed().toStdString();
		if (server.empty())
			continue;

		std::string name = "multistream_" + std::to_string(i);
		obs_data_t *sd = obs_data_create();
		obs_data_set_string(sd, "server", server.c_str());
		obs_data_set_string(sd, "key", key.c_str());
		obs_service_t *svc = obs_service_create("rtmp_custom", (name + "_svc").c_str(), sd, nullptr);
		obs_data_release(sd);

		obs_output_t *out = obs_output_create("rtmp_output", (name + "_out").c_str(), nullptr, nullptr);
		obs_output_set_service(out, svc);
		obs_output_set_video_encoder(out, venc);
		obs_output_set_audio_encoder(out, aenc, 0);

		if (obs_output_start(out)) {
			g->outs[i] = out;
			g->svcs[i] = svc; // muss leben, solange der Output läuft
			blog(LOG_INFO, "[multistream-chat] Ziel %d gestartet", i + 1);
		} else {
			blog(LOG_WARNING, "[multistream-chat] Ziel %d Fehler: %s", i + 1,
			     obs_output_get_last_error(out));
			obs_output_release(out);
			obs_service_release(svc);
		}
	}
	obs_output_release(main);
}

static void on_frontend_event(enum obs_frontend_event event, void *)
{
	switch (event) {
	case OBS_FRONTEND_EVENT_STREAMING_STARTED:
		save_settings();
		start_targets();
		break;
	case OBS_FRONTEND_EVENT_STREAMING_STOPPING:
		stop_targets();
		break;
	case OBS_FRONTEND_EVENT_EXIT:
		save_settings();
		stop_targets();
		break;
	default:
		break;
	}
}

// ---------------------------------------------------------------- Chat-Overlay

static void create_chat_source()
{
	save_settings();

	char *file = obs_module_file("chat.html");
	if (!file) {
		blog(LOG_WARNING, "[multistream-chat] chat.html nicht gefunden");
		return;
	}
	QString path = QFileInfo(QString::fromUtf8(file)).absoluteFilePath();
	bfree(file);

	QUrl url = QUrl::fromLocalFile(path);
	QUrlQuery q;
	q.addQueryItem("twitch", g->twitch->text().trimmed());
	q.addQueryItem("ytkey", g->ytKey->text().trimmed());
	q.addQueryItem("ytvideo", g->ytVideo->text().trimmed());
	url.setQuery(q);

	obs_data_t *s = obs_data_create();
	obs_data_set_bool(s, "is_local_file", false);
	obs_data_set_string(s, "url", url.toString(QUrl::FullyEncoded).toUtf8().constData());
	obs_data_set_int(s, "width", 450);
	obs_data_set_int(s, "height", 800);
	obs_data_set_string(s, "css", "body { background-color: rgba(0,0,0,0); margin: 0; overflow: hidden; }");

	obs_source_t *existing = obs_get_source_by_name("Multichat");
	if (existing) {
		obs_source_update(existing, s);
		obs_source_release(existing);
	} else {
		obs_source_t *src = obs_source_create("browser_source", "Multichat", s, nullptr);
		obs_source_t *sceneSrc = obs_frontend_get_current_scene();
		if (src && sceneSrc) {
			obs_scene_t *scene = obs_scene_from_source(sceneSrc);
			obs_scene_add(scene, src);
		}
		if (sceneSrc)
			obs_source_release(sceneSrc);
		if (src)
			obs_source_release(src);
	}
	obs_data_release(s);
}

// ---------------------------------------------------------------- Dock

static QWidget *build_panel()
{
	auto *root = new QWidget();
	auto *lay = new QVBoxLayout(root);

	// Chat
	auto *chatBox = new QGroupBox("Chat-Overlay (Twitch + YouTube)");
	auto *cg = new QGridLayout(chatBox);
	g->twitch = new QLineEdit();
	g->twitch->setPlaceholderText("Twitch-Kanalname");
	g->ytKey = new QLineEdit();
	g->ytKey->setEchoMode(QLineEdit::Password);
	g->ytKey->setPlaceholderText("YouTube API-Key");
	g->ytVideo = new QLineEdit();
	g->ytVideo->setPlaceholderText("YouTube Video-ID des Livestreams");
	cg->addWidget(new QLabel("Twitch:"), 0, 0);
	cg->addWidget(g->twitch, 0, 1);
	cg->addWidget(new QLabel("YT API-Key:"), 1, 0);
	cg->addWidget(g->ytKey, 1, 1);
	cg->addWidget(new QLabel("YT Video-ID:"), 2, 0);
	cg->addWidget(g->ytVideo, 2, 1);
	auto *chatBtn = new QPushButton("Chat-Overlay in aktueller Szene erstellen / aktualisieren");
	cg->addWidget(chatBtn, 3, 0, 1, 2);
	lay->addWidget(chatBox);
	QObject::connect(chatBtn, &QPushButton::clicked, [] { create_chat_source(); });

	// Ziele
	auto *tBox = new QGroupBox("Zusätzliche Stream-Ziele");
	auto *tg = new QGridLayout(tBox);
	for (int i = 0; i < SLOTS; i++) {
		g->enable[i] = new QCheckBox(QString("Ziel %1").arg(i + 1));
		g->server[i] = new QLineEdit();
		g->server[i]->setPlaceholderText("rtmp://...");
		g->key[i] = new QLineEdit();
		g->key[i]->setEchoMode(QLineEdit::Password);
		g->key[i]->setPlaceholderText("Stream-Key");
		tg->addWidget(g->enable[i], i * 2, 0);
		tg->addWidget(g->server[i], i * 2, 1);
		tg->addWidget(g->key[i], i * 2 + 1, 1);
	}
	lay->addWidget(tBox);

	auto *hint = new QLabel("Die Zusatzziele starten und stoppen automatisch mit \"Stream starten\".");
	hint->setWordWrap(true);
	lay->addWidget(hint);
	lay->addStretch();
	return root;
}

// ---------------------------------------------------------------- Modul

bool obs_module_load(void)
{
	blog(LOG_INFO, "[multistream-chat] geladen");
	return true;
}

void obs_module_post_load(void)
{
	g = new State();
	QWidget *panel = build_panel();
	load_settings();
	if (!obs_frontend_add_dock_by_id("multistream_chat_dock", "Multistream", panel)) {
		blog(LOG_WARNING, "[multistream-chat] Dock konnte nicht erstellt werden (OBS 30+ nötig)");
	}
	obs_frontend_add_event_callback(on_frontend_event, nullptr);
}

void obs_module_unload(void)
{
	obs_frontend_remove_event_callback(on_frontend_event, nullptr);
	stop_targets();
	// g wird absichtlich nicht gelöscht: die Widgets gehören OBS
}

const char *obs_module_name(void)
{
	return "obs-multistream-chat";
}
