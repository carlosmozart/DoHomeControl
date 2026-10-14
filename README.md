# DoHome Control

App Android para controlar lâmpadas inteligentes **DoHome** (fabricante Doit) pela rede local, sem nuvem e sem o app oficial, que saiu da Play Store.

Foi feito para uma lâmpada **DT-WY** (branca, quente/fria, chip W600, firmware 1.0.4), mas também suporta os modelos coloridos (**DT-WYRGB**, fitas **STRIPE**).

## Funcionalidades

- Liga/desliga, brilho e temperatura de cor (3000 K – 6400 K)
- Cor, saturação e 18 efeitos nas lâmpadas RGB (abas escondidas nas lâmpadas só brancas)
- Detecta o modelo da lâmpada automaticamente
- Busca a lâmpada na rede (broadcast UDP) e, se o roteador bloquear, varre a sub-rede IP por IP
- **Configura o Wi-Fi da lâmpada** (substitui o pareamento do app oficial) e descobre o IP novo sozinho
- **Modo direto**: controla a lâmpada conectado ao Wi-Fi `DoHome_xxxx` dela, sem roteador
- **Widget na tela inicial**: liga/desliga e brilho −/+ em passos de 25%
- Tema claro/escuro e animações (cascata, mola, pulsar, número contando)

## Como usar

1. Lâmpada nova ou resetada cria o Wi-Fi `DoHome_xxxx`. Conecte o celular nele.
2. No app, toque no ícone de **Wi-Fi**, digite o nome e a senha da sua rede (**2,4 GHz**) e envie.
3. A lâmpada entra na sua rede. Volte o celular para o Wi-Fi de casa; o app já sabe o IP novo (ou toque em **Procurar**).
4. Opcional: adicione o widget **DoHome** na tela inicial.

## Compilar

Requer JDK 21 e Android SDK 36.

```bash
./gradlew assembleRelease
```

O build vai para `%LOCALAPPDATA%/DoHomeControlBuild` em vez de `app/build` (a sincronização do OneDrive travava a pasta de build). O APK de release é assinado com a chave de debug, para instalar direto no celular.

## Protocolo

Tudo pela rede local:

| O quê | Como |
|---|---|
| Comandos | JSON + `\r\n` via **TCP 5555**, ex.: `{"cmd":6,"r":0,"g":0,"b":0,"w":5000,"m":0}` |
| Descoberta | `cmd=ping` em broadcast **UDP 6091**; a resposta traz `sta_ip`, `device_name`, `device_type` |
| Modo AP | a lâmpada fica em `192.168.4.1` |
| Valores | cada canal vai de 0 a 5000; `w`/`m` são os dois LEDs brancos |

| `cmd` | Função |
|---|---|
| 3 | reiniciar |
| 4 | info do dispositivo (`dev_id` = `<mac>_<modelo>_<chip>`) |
| 6 | definir r/g/b/w/m |
| 7 | efeito (`index` 1–27) |
| 16 | credenciais Wi-Fi (`ssid`, `pass`) |
| 25 | ler estado |

**Descoberta importante (DT-WY, firmware 1.0.4):** não mande `"on":1` no `cmd 6`. Com esse campo, a lâmpada ignora os valores e volta ao brilho máximo. Para desligar, mande todos os canais em 0.

## Estrutura

```
app/src/main/java/com/carlos/dohome/
├── DoHomeClient.kt   # protocolo: TCP, descoberta UDP, varredura da sub-rede
├── LightStore.kt     # estado salvo, compartilhado entre o app e o widget
├── LightViewModel.kt # lógica da tela
├── MainActivity.kt   # interface (Jetpack Compose)
└── DoHomeWidget.kt   # widget da tela inicial (RemoteViews)
```

## Créditos

- Protocolo baseado na biblioteca [dohome-api](https://github.com/mishamyrt/dohome_api) (MIT), de Mikhael Khrustik, e no repositório oficial [SmartArduino/DoHome](https://github.com/SmartArduino/DoHome).
- Nomes e curvas das animações da [ai-motion-library](https://github.com/apenasdouglas/ai-motion-library) (MIT).
