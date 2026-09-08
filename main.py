"""
Mini-IDE für Android (Kivy)
============================
Wird via buildozer zu einer APK gebaut.

Features:
- Datei-/Ordnerbrowser
- Mehrere Tabs zum Bearbeiten
- Syntax-Highlighting (Pygments über CodeInput)
- Speichern / Speichern unter
- Einfacher Kommando-Runner

WICHTIG: Android sperrt Apps in eine Sandbox. Ein echtes Terminal mit Zugriff
auf beliebige Systembefehle wie unter Windows/Termux ist in einer normalen
APK NICHT möglich. Der Kommando-Runner hier funktioniert nur eingeschränkt.
"""

import os
import subprocess

from kivy.app import App
from kivy.uix.boxlayout import BoxLayout
from kivy.uix.button import Button
from kivy.uix.filechooser import FileChooserListView
from kivy.uix.tabbedpanel import TabbedPanel, TabbedPanelItem
from kivy.uix.codeinput import CodeInput
from kivy.uix.popup import Popup
from kivy.uix.textinput import TextInput
from kivy.uix.label import Label
from pygments.lexers import PythonLexer


class EditorTabContent(BoxLayout):
    """Ein Tab-Inhalt: ein CodeInput-Feld mit Syntax-Highlighting."""

    def __init__(self, filepath=None, **kwargs):
        super().__init__(orientation="vertical", **kwargs)
        self.filepath = filepath
        self.code_input = CodeInput(lexer=PythonLexer(), font_size=14)
        if filepath and os.path.isfile(filepath):
            with open(filepath, "r", encoding="utf-8", errors="replace") as f:
                self.code_input.text = f.read()
        self.add_widget(self.code_input)

    def save(self, path=None):
        target = path or self.filepath
        if not target:
            return False
        with open(target, "w", encoding="utf-8") as f:
            f.write(self.code_input.text)
        self.filepath = target
        return True


class MiniIDERoot(BoxLayout):
    def __init__(self, **kwargs):
        super().__init__(orientation="vertical", **kwargs)

        # -- Toolbar --
        toolbar = BoxLayout(size_hint=(1, 0.07))
        btn_toggle_files = Button(text="Dateien")
        btn_toggle_files.bind(on_release=self.toggle_filechooser)
        btn_new = Button(text="Neu")
        btn_new.bind(on_release=lambda x: self.new_tab())
        btn_save = Button(text="Speichern")
        btn_save.bind(on_release=lambda x: self.save_current())
        btn_run = Button(text="Befehl")
        btn_run.bind(on_release=self.show_command_popup)
        for b in (btn_toggle_files, btn_new, btn_save, btn_run):
            toolbar.add_widget(b)
        self.add_widget(toolbar)

        # -- Body: Dateibrowser + Tabs --
        body = BoxLayout(orientation="horizontal")
        self.filechooser = FileChooserListView(
            path=os.path.expanduser("~"), size_hint=(0.35, 1)
        )
        self.filechooser.bind(on_submit=self.on_file_double_tap)
        self.filechooser_visible = True
        body.add_widget(self.filechooser)

        self.tabs = TabbedPanel(do_default_tab=False, size_hint=(0.65, 1))
        body.add_widget(self.tabs)
        self.add_widget(body)

        self.new_tab()

    def toggle_filechooser(self, *args):
        if self.filechooser_visible:
            self.filechooser.size_hint = (0, 1)
            self.filechooser.opacity = 0
            self.filechooser.disabled = True
        else:
            self.filechooser.size_hint = (0.35, 1)
            self.filechooser.opacity = 1
            self.filechooser.disabled = False
        self.filechooser_visible = not self.filechooser_visible

    def on_file_double_tap(self, chooser, selection, touch):
        if not selection:
            return
        path = selection[0]
        if os.path.isfile(path):
            self.open_file(path)

    def open_file(self, path):
        content = EditorTabContent(filepath=path)
        tab = TabbedPanelItem(text=os.path.basename(path))
        tab.add_widget(content)
        self.tabs.add_widget(tab)
        self.tabs.switch_to(tab)

    def new_tab(self):
        content = EditorTabContent(filepath=None)
        tab = TabbedPanelItem(text="Unbenannt")
        tab.add_widget(content)
        self.tabs.add_widget(tab)
        self.tabs.switch_to(tab)

    def current_content(self):
        current_tab = self.tabs.current_tab
        if not current_tab or not current_tab.children:
            return None
        return current_tab.children[0]

    def save_current(self):
        content = self.current_content()
        if not content:
            return
        if content.filepath:
            content.save()
        else:
            self.show_saveas_popup(content)

    def show_saveas_popup(self, content):
        layout = BoxLayout(orientation="vertical", padding=10, spacing=10)
        default_path = os.path.join(os.path.expanduser("~"), "neue_datei.py")
        text_input = TextInput(text=default_path, size_hint=(1, 0.3), multiline=False)
        save_btn = Button(text="Speichern", size_hint=(1, 0.3))
        layout.add_widget(Label(text="Pfad eingeben:", size_hint=(1, 0.2)))
        layout.add_widget(text_input)
        layout.add_widget(save_btn)
        popup = Popup(title="Speichern unter", content=layout, size_hint=(0.9, 0.4))

        def do_save(*args):
            path = text_input.text.strip()
            if path:
                content.save(path)
                self.tabs.current_tab.text = os.path.basename(path)
            popup.dismiss()

        save_btn.bind(on_release=do_save)
        popup.open()

    def show_command_popup(self, *args):
        layout = BoxLayout(orientation="vertical", padding=10, spacing=10)
        output = TextInput(readonly=True, size_hint=(1, 0.6))
        cmd_input = TextInput(size_hint=(1, 0.15), multiline=False)
        run_btn = Button(text="Ausführen", size_hint=(1, 0.15))
        layout.add_widget(
            Label(text="Befehl (stark eingeschränkt durch Android-Sandbox):", size_hint=(1, 0.1))
        )
        layout.add_widget(output)
        layout.add_widget(cmd_input)
        layout.add_widget(run_btn)
        popup = Popup(title="Befehl ausführen", content=layout, size_hint=(0.95, 0.6))

        def run_cmd(*args):
            cmd = cmd_input.text.strip()
            if not cmd:
                return
            try:
                result = subprocess.run(
                    cmd, shell=True, capture_output=True, text=True, timeout=15
                )
                output.text = result.stdout + result.stderr
            except Exception as e:
                output.text = f"Fehler: {e}"

        run_btn.bind(on_release=run_cmd)
        popup.open()


class MiniIDEApp(App):
    def build(self):
        return MiniIDERoot()


if __name__ == "__main__":
    MiniIDEApp().run()
