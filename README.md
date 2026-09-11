# LUMOS AI

A ChatGPT-style local Android AI, built on your existing **llama.cpp + Dolphin GGUF** setup.
Open LUMOS → local AI service is detected → chat, with token-by-token streaming and
persistent history. No cloud APIs, no automatic uploads.

This implements **Stage A** of the NEXUS architecture plan: the Android app talks over
HTTP to `llama-server` running in **Termux**, so no native inference debugging is needed.

## Current milestone (Phase 1–2)

✅ Open LUMOS → type "Hello" → Dolphin responds with streaming tokens
✅ Close and reopen → conversation remains (Room database)
✅ Server status dot in the chat header (online/offline polling)
✅ Stop / Copy / Regenerate on assistant messages
✅ Settings: server URL, model name, temperature, context size, CPU threads, system prompt
✅ Conversation history list with new chat + delete

## Phase 0 — start the backend (Termux)

Your existing Termux + llama.cpp setup stays untouched. Just run the server instead of
the CLI:

```bash
# in Termux (model path as before)
llama-server -m dolphin-3b-iq4_xs.gguf --port 8080 -c 4096 -t 4 --host 127.0.0.1
```

Verify it works:

```bash
curl http://127.0.0.1:8080/health        # -> "ok"
curl http://127.0.0.1:8080/v1/chat/completions       -H "Content-Type: application/json"       -d '{"model":"dolphin-3b-iq4_xs.gguf","messages":[{"role":"user","content":"Hello!"}],"stream":true}'
```

Keep Termux running in the background (wake-lock: `termux-wake-lock`).

## Build the app

1. Open this folder in **Android Studio** (Hedgehog or newer).
2. Let Gradle sync (it will generate the wrapper automatically).
3. Run on your device (`minSdk 26`, i.e. Android 8.0+). The phone and Termux run on the
   same device, so the app reaches the server at `http://127.0.0.1:8080` by default.

## Project structure

```
app/src/main/java/com/lumos/ai/
├── MainActivity.kt            # NavHost: home / chat / settings
├── data/
│   ├── db/                    # Room: ConversationEntity, MessageEntity, DAOs, database
│   ├── ConversationRepository.kt
│   └── SettingsRepository.kt  # DataStore preferences
├── network/
│   ├── LlamaApiClient.kt      # OkHttp + SSE streaming from llama-server
│   └── ServerManager.kt       # health checks (hides raw Termux from the user)
└── ui/
    ├── chat/                  # ChatScreen + ChatViewModel (stream, stop, regenerate)
    ├── home/                  # conversation history
    └── settings/
```

## Mapping to the plan

| Plan module          | Implementation                              |
|----------------------|---------------------------------------------|
| ChatEngine           | `LlamaApiClient.buildChatBody` (system prompt + last 12 msgs) |
| LlamaApiClient       | `network/LlamaApiClient.kt` (SSE token stream) |
| ServerManager        | `network/ServerManager.kt` + status dot     |
| ConversationRepository | `data/ConversationRepository.kt` (Room)   |
| ModelManager (basic) | model name + settings fields (GGUF browser = Phase 3) |
| SettingsRepository   | `data/SettingsRepository.kt` (DataStore)    |

## Security model

Local-first: the only network access is to the loopback address of your own device.
No permissions beyond basic network state, no accounts, no analytics.

## Roadmap from here

- **Phase 3**: GGUF file browser in-app, per-model configs, server start/stop controls.
- **Phase 4**: Markdown/code rendering, dark-mode toggle, search, perf stats.
- **Phase 5**: bundle llama.cpp natively (drop Termux).
- **Phase 6**: optional local tools (files, terminal, RAG, voice).

---
*LUMOS — let there be (local) light.* 🔆
