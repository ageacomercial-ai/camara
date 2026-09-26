# Instalar o Câmara Pro no telemóvel

Tens 2 caminhos. Escolhe um.

## Opção A — Sem instalar nada (recomendado, internet lenta)

O APK é gerado na nuvem pelo GitHub Actions.

1. Cria um repositório no GitHub e envia a pasta `CameraPro`:
   - `git init`, `git add .`, `git commit -m "CameraPro"`, `git push`
2. No GitHub, abre o separador **Actions** → espera o run **Build APK** ficar verde (~3–5 min).
3. Descarrega o artefacto **CameraPro-debug** (ficheiro `app-debug.apk`).
4. Passa o `.apk` para o telemóvel (WhatsApp, Drive, USB ou e-mail).
5. No telemóvel: abre o ficheiro → **Instalar** → permite **"Instalar apps desconhecidas"** se pedir.
6. Abre o **Câmara Pro** → permite **Câmara** e **Microfone**.

Requisitos do telemóvel: Android 8.0 ou mais recente.

## Opção B — Com Android Studio (no PC)

1. Instala o **Android Studio** (versão Koala ou mais recente).
2. `File → Open` → escolhe a pasta `CameraPro` (a que tem o `gradlew.bat`).
3. Espera o **Gradle Sync** terminar (primeira vez demora).
4. Liga o telemóvel por USB com **Depuração USB** ligada
   (`Definições → Opções de programador → Depuração USB`).
5. Prime **Run ▶** (ou `Shift+F10`). O Android Studio instala e abre a app.
6. Aceita as permissões de **Câmara** e **Microfone** no telemóvel.

Se der erro de SDK em falta: `Tools → SDK Manager` → instala
**Android 14 (API 34)**, **Build-Tools 34** e **Platform-Tools**,
ou copia `local.properties.example` para `local.properties` com o teu caminho.

## Resolução de problemas

- **"App não instalada"**: desinstala versões antigas com o mesmo nome
  (`com.agea.camerapro`) e tenta de novo.
- **Ecrã preto na pré-visualização**: testa num telemóvel real
  (emulador não tem boa câmara) e confirma as permissões em
  `Definições → Apps → Câmara Pro → Permissões`.
- **Erro de compilação no `CameraEffect`**: já está corrigido via
  `FilterEffect.kt` (o construtor original é `protected`).
  Faz `Build → Clean Project` e volta a compilar.
- **Vídeo sem filtro gravado**: o filtro é aplicado pelo `CameraFilterProcessor`
  ao `PREVIEW` e ao `VIDEO_CAPTURE` em simultâneo — se um aparelho muito antigo
  não suportar `CameraEffect`, a gravação sai sem filtro mas a app continua a funcionar.
