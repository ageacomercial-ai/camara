# Câmara Pro — app Android nativo

Projecto Kotlin/Jetpack Compose completo, usando CameraX. Ao contrário da versão web,
os controlos de estabilização e anti-cintilação aqui **mexem mesmo no hardware da
câmara** (via Camera2 interop), e os filtros são aplicados por um pipeline OpenGL
que é gravado directamente no ficheiro de vídeo, não só mostrado no ecrã.

## Como abrir e compilar
1. Instala o **Android Studio** (versão Koala ou mais recente).
2. `File → Open` e escolhe a pasta `CameraPro`. O Android Studio gera o `gradlew`
   e sincroniza as dependências automaticamente na primeira abertura.
3. Liga um telemóvel Android (Definições → Opções de programador → Depuração USB)
   ou usa um emulador com câmara virtual.
4. `Run ▶`.

Requisitos: minSdk 26 (Android 8.0+), compileSdk 34.

## O que é real, feature a feature
- **Estabilização** — `CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE`, ligado/desligado
  ao vivo via `Camera2CameraControl`, sem precisar de reiniciar a câmara. Nos telemóveis
  que só têm EIS (a maioria), o Android decide o método; em alguns Android 13+ com OIS
  físico, o sistema pode também activar estabilização de pré-visualização.
- **Anti-cintilação** — `CaptureRequest.CONTROL_AE_ANTIBANDING_MODE` (AUTO ↔ OFF),
  o mesmo mecanismo que os apps de câmara nativos usam para 50 Hz/60 Hz.
- **Resolução 4K / 1080p / 720p** — `QualitySelector` do CameraX (`Quality.UHD/FHD/HD`),
  usa a resolução máxima que a câmara e o codificador do aparelho suportarem.
- **Filtros** — pipeline OpenGL ES (`CameraFilterProcessor` + `GLUtil.kt`) partilhado
  entre a pré-visualização e a gravação, via `CameraEffect` do CameraX (API estável
  desde a versão 1.3). É a parte mais avançada do código — se abrires no Android
  Studio e a versão do CameraX instalada tiver pequenas diferenças de assinatura
  nesta API (`SurfaceProcessor`/`SurfaceOutput`), o autocomplete do IDE resolve-as
  rapidamente; o resto do projecto (câmara, gravação, permissões, UI) é código
  CameraX/Compose estável e directo.
- **Gravação** — grava `.mp4` com áudio directamente para `Filmes/CameraPro` via
  `MediaStore`, visível na galeria do telemóvel sem passos extra.
- **Torch/flash** — `CameraControl.enableTorch`, real, com detecção se o aparelho tem
  flash.

## Estrutura
```
app/src/main/java/com/agea/camerapro/
  MainActivity.kt        — permissões + arranque
  CameraScreen.kt         — UI Compose (pré-visualização, filtros, controlos)
  CameraController.kt     — liga o CameraX, controla hardware, grava
  CameraFilterProcessor.kt — pipeline de filtros (CameraX SurfaceProcessor)
  GLUtil.kt                — shader GLSL + EGL
  FilterPreset.kt           — os 8 filtros profissionais
  Theme.kt                  — paleta e ecrã de permissões
```

Este ambiente onde o código foi escrito não tem Xcode nem Android Studio, por isso
não foi possível compilar/testar aqui — o próximo passo é abrir no Android Studio,
corrigir o que o compilador assinalar (normal em qualquer projecto CameraX com
efeitos) e testar num telemóvel real, já que a câmara não funciona bem em
emuladores.
