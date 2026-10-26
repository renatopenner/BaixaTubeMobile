# BaixaTube Mobile

Versão Android do BaixaTube: baixa vídeos e áudios de **YouTube, Instagram,
Facebook e TikTok** direto no celular, sem PC ligado e sem servidor.

Motor: **yt-dlp + FFmpeg compilados para Android** (`youtubedl-android` 0.18.1),
rodando 100% no aparelho. A interface é Kotlin + Jetpack Compose com a mesma
paleta do BaixaTube de Windows (`app/theme.py` do projeto desktop).

## O que faz

- Analisa o link e lista os formatos reais oferecidos pelo site (144p até 4K,
  H.264/VP9/AV1/HEVC, AAC/Opus).
- Presets iguais aos do desktop: Melhor MP4, Original (MKV sem reencode), 4K,
  2K, Full HD, HD, 480p, 360p, MP3 320, M4A e Opus.
- **Presets de resolução não exigem H.264**: o YouTube não oferece H.264 acima
  de 1080p, então "4K" pega o melhor disponível (AV1/VP9 em MP4).
- **Atualiza o yt-dlp sozinho** (no máximo 1x por dia, em segundo plano) — motor
  velho é a causa clássica de `HTTP Error 403` nos formatos altos.
- Runtime JavaScript (QuickJS, `libqjs.so` da lib) habilitado via
  `--js-runtimes quickjs:<path>` — exigência das versões novas do yt-dlp.
- Fila com progresso, ETA, cancelamento e dois downloads simultâneos.
- Metadados e capa embutidos no arquivo (quando o contêiner aceita); as capas
  soltas e restos de download interrompido são apagados.
- Nome do arquivo inclui a resolução (`Título [id] 1920x1080.mp4`) para não
  colidir quando você baixa o mesmo vídeo em qualidades diferentes.
- Playlists: alterna "baixar a lista inteira".
- **Aba "Baixados"**: lista os arquivos da pasta com tamanho e data, com Abrir,
  Compartilhar, Excluir (com confirmação) e botão para abrir a pasta no
  gerenciador de arquivos do sistema.
- "Acesso a todos os arquivos" (opcional): libera ver e apagar também os
  downloads antigos que o Android esconde por scoped storage.
- Abre/compartilha o arquivo baixado e mostra a pasta.
- Importa `cookies.txt` para Instagram/Facebook (e TikTok quando exigir login).
- Botão para atualizar o yt-dlp na hora, em Ajustes.

Arquivos baixados: `Download/BaixaTube/` no armazenamento interno.

## Requisitos

- Android 8.0 (API 26) ou superior.
- APK por arquitetura: `arm64-v8a` (celulares atuais) ou `armeabi-v7a` (antigos).

## Como compilar

Ferramentas fora do PATH (instaladas em `C:\dev`, sem Android Studio):

```
C:\dev\jdk17              JDK 17 (Temurin)
C:\dev\android-sdk        cmdline-tools + platform-35 + build-tools-35
C:\dev\gradle-8.11.1      Gradle
```

```powershell
$env:JAVA_HOME="C:\dev\jdk17"
C:\dev\gradle-8.11.1\bin\gradle.bat -p C:\BaixaTubeMobile assembleRelease
```

Saída em `app\build\outputs\apk\release\`. Assinatura: `certs\baixatube.jks`
(senha em `keystore.properties`, ambos fora do git).

Para testar em emulador x86_64 (precisa da imagem `system-images;android-34;google_apis;x86_64`):

```powershell
C:\dev\gradle-8.11.1\bin\gradle.bat -p C:\BaixaTubeMobile assembleRelease -Pabis=x86_64
```

## Estrutura

```
app/src/main/java/br/com/renatopenner/baixatube/
    MainActivity.kt   atividade + tela principal + barra de topo
    MainViewModel.kt  estado, análise, fila (2 downloads simultâneos), cookies
    Engine.kt         motor: análise (-J + org.json), download, cancelamento
    Models.kt         Fmt, VideoMeta, Preset (mesmos seletores do desktop), Job
    Ui.kt             cards, chips, tabela de formatos, ajustes, intents
    Theme.kt          paleta clara/escura herdada do BaixaTube desktop
```

## Limitações conhecidas

- Instagram e Facebook exigem sessão logada: exporte o `cookies.txt` do
  navegador (extensão "Get cookies.txt") e importe em **Ajustes → Cookies**.
- YouTube e TikTok funcionam sem cookies (testado em emulador Android 14).
- Downloads só podem ir para `Download/` (restrição de armazenamento do Android).
- O APK é assinado com certificado próprio: instalar exige permitir
  "instalar de fontes desconhecidas"; o Play Protect pode pedir confirmação.
