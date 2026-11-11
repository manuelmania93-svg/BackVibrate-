# BackVibrate – Build-Anleitung (Git Bash)

Dieses Projekt lässt dein Handy beim Tippen auf die Zurück-Taste vibrieren
(Workaround für fehlendes Haptik-Feedback auf Honor/MagicOS).

## 1. Voraussetzungen einmalig installieren

Du brauchst 3 Dinge auf deinem PC (nicht auf dem Handy):

1. **JDK 17** – https://adoptium.net (Temurin 17, "MSI" für Windows)
2. **Android SDK Command-Line Tools** – https://developer.android.com/studio#command-tools
   (den Ordner z. B. nach `C:\Android\cmdline-tools\latest` entpacken)
3. **Git Bash** hast du schon.

Danach in Git Bash prüfen:
```bash
java -version
```

Umgebungsvariablen setzen (in Git Bash, z. B. in `~/.bashrc` eintragen):
```bash
export ANDROID_HOME="/c/Android"
export PATH="$PATH:$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools"
```
Dann `source ~/.bashrc` und neu öffnen.

Android SDK-Pakete installieren:
```bash
sdkmanager "platform-tools" "platforms;android-34" "build-tools;34.0.0"
```
(Lizenzen mit `sdkmanager --licenses` bestätigen, jeweils `y` drücken.)

## 2. Projekt entpacken und in ein Git-Repo verwandeln

```bash
cd /c/Users/DEIN_NAME/Projekte
unzip BackVibrate.zip
cd BackVibrate
git init
git add .
git commit -m "Initial commit: BackVibrate Accessibility Service"
```

## 3. Gradle Wrapper erzeugen (einmalig, braucht Internet)

Da wir keinen fertigen Wrapper mitliefern können, erzeugst du ihn einmal
lokal (dafür brauchst du eine lokale Gradle-Installation, z. B. via
`choco install gradle` oder von https://gradle.org/releases herunterladen
und in den PATH legen):

```bash
gradle wrapper --gradle-version 8.7
```

Danach hast du `gradlew` bzw. `gradlew.bat` im Ordner und musst Gradle
nicht mehr global installiert haben.

## 4. App bauen

```bash
./gradlew assembleDebug
```

Die fertige APK liegt danach unter:
```
app/build/outputs/apk/debug/app-debug.apk
```

## 5. Auf dem Handy installieren

USB-Debugging auf dem Honor-Handy aktivieren (Entwickleroptionen), Handy
per USB anschließen, dann:

```bash
adb devices          # prüfen, ob das Gerät erkannt wird
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## 6. App einrichten

1. App "BackVibrate" öffnen
2. Button "Overlay-Berechtigung erteilen" antippen → erlauben
3. Button "Bedienungshilfe aktivieren" antippen → in der Liste
   "BackVibrate" suchen und aktivieren

Danach sollte das Handy vibrieren, wenn du auf den unsichtbaren Bereich
über der Zurück-Taste tippst.

## Wichtig: Position/Größe des Overlays anpassen

In `BackVibrateAccessibilityService.kt` sind Breite/Höhe/Position des
unsichtbaren Touch-Feldes (aktuell 200x120 px, unten links) nur ein
Startwert. Du musst sie an deine Bildschirmauflösung und die genaue
Position deiner Zurück-Taste in der 3-Tasten-Leiste anpassen – am besten
per Trial-and-Error: Wert ändern, `./gradlew installDebug`, testen.

## Änderungen schnell testen (nach der ersten Einrichtung)

```bash
./gradlew installDebug
```
Das baut die App neu und installiert sie direkt über adb – Berechtigungen
bleiben erhalten, du musst sie nicht erneut vergeben.

## Selbst-Einschalten nach Neustart (einmalig per PC freigeben)

Honor/MagicOS schaltet den Bedienungshilfen-Dienst nach manchen Neustarts
wieder aus. Damit sich die App nach jedem Neustart selbst wieder
aktiviert, erteilst du ihr einmalig per USB eine Spezial-Berechtigung:

1. App installieren und mindestens einmal öffnen.
2. Handy per USB anschließen, dann in Git Bash:

```bash
adb shell pm grant com.example.backvibrate android.permission.WRITE_SECURE_SETTINGS
```

Das war's. Ab jetzt gilt:
- Nach jedem Neustart schaltet sich der Dienst innerhalb von wenigen
  Sekunden von selbst wieder ein (kein Shizuku nötig).
- Die App prüft danach mehrfach nach (max. 3 Versuche über ca. 3 Minuten):
  Schaltet MagicOS den Dienst erst einige Sekunden nach dem Neustart wieder
  aus, wird er erneut aktiviert, bevor die Warn-Benachrichtigung kommt.
- Dieselbe Prüfung läuft auch nach einem Update der App (installiert über
  die bestehende Installation, ohne Deinstallieren).
- Der Schalter "Selbst-Einschalten aktivieren" in der App steuert die
  Automatik (standardmäßig an).
- Die Status-Karte in der App zeigt unter "ADB-Schutz", ob die
  PC-Freigabe noch vorhanden ist. Sie geht verloren, wenn die App
  deinstalliert und neu installiert wird - deshalb immer nur darüber
  installieren, nie deinstallieren.
- Ohne die Berechtigung bekommst du weiterhin nur die Benachrichtigung
  zum manuellen Einschalten.

Optional: Wer keinen PC nutzen will, kann die Berechtigung stattdessen
über die App **Shizuku** freigeben (Shizuku starten, in BackVibrate auf
den Button tippen, Zugriff erlauben). Das läuft nur, wenn der
Shizuku-Dienst aktiv ist.

Hinweis für eigene Builds: im Repo liegt ein fester `debug.keystore`,
damit Updates aus dem Workflow installierbar bleiben. Wer vorher eine
APK aus dem Workflow installiert hat, muss die App einmalig deinstallieren
und neu installieren, weil sich der Signatur-Schlüssel geändert hat.
