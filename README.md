# Bluetooth HFP Voice-Channel Call Router (FiltroLlamadas)

![Java](https://img.shields.io/badge/Language-Java-orange.svg)
![Platform](https://img.shields.io/badge/Platform-Android%20Services-green.svg)
![Audio](https://img.shields.io/badge/Audio-Bluetooth%20HFP%20%2F%20SCO-blue.svg)
![Concurrency](https://img.shields.io/badge/Pattern-Event--Driven%20State%20Machine-lightgrey.svg)

A native Android background service in **Java** that coordinates `NotificationListenerService`, `BroadcastReceiver`, and `ContactsContract` to identify incoming callers and announce them via `TextToSpeech` over a Bluetooth helmet intercom right as the call is answered physically.

---

## Engineering Overview & Problem Statement

When riding a motorcycle with a Bluetooth helmet intercom, incoming phone calls cannot be visually screened without stopping. Auto-rejecting unknown numbers risks missing critical calls (such as medical follow-ups or job recruiters), while standard caller-ID announcers often fail because Android's Bluetooth Hands-Free Profile (`STREAM_VOICE_CALL`) takes several hundred milliseconds to establish an active audio channel upon answering.

This application solves both constraints using an **event-driven state machine with hardware latency compensation**:
1. **Caller Identification (`RINGING` State):** Intercepts real-time caller metadata from CallApp notifications via `NotificationListenerService` and cross-references the incoming number against the local address book (`ContactsContract`).
2. **Non-Destructive Routing:** Never drops or blocks incoming calls, preserving reachability for priority unknown numbers.
3. **Hardware Latency Compensation (`OFFHOOK` State):** Upon detecting call pickup via the intercom's physical button (`TelephonyManager.EXTRA_STATE_OFFHOOK`), the service executes a calibrated **600 ms settling delay** to let the Bluetooth SCO/HFP voice channel lock before speaking `"Llamada de [Contact]"` or `"Desconocido: [Caller ID]"`.

---

## Execution Sequence

```text
Incoming Call (TelephonyManager: RINGING)
   │
   ├──► NotificationListenerService captures Caller ID string (CallApp)
   │
   ├──► ContactsContract queries local phonebook (Saved vs. Unknown)
   │
   ▼
User presses physical button on Helmet Intercom (Y10 2X)
   │
   ▼
BroadcastReceiver detects PHONE_STATE == OFFHOOK
   │
   ├──► [600 ms Hardware Settling Window: Bluetooth STREAM_VOICE_CALL lock]
   │
   ▼
TextToSpeech synthesizes announcement directly into active voice channel:
   • Saved Contact:   "Llamada de [Contact Name]"
   • Unknown Caller:  "Desconocido: [Identified Name]"
```

---

## Key Technical Highlights

* **Concurrent Service Coordination:** Synchronizes asynchronous notification callbacks (`NotificationListenerService`) with system telephony state broadcasts (`PHONE_STATE`) cleanly without race conditions.
* **Deterministic Bluetooth Audio Timing:** Empirical 600 ms delay calibrated for Bluetooth HFP/SCO channel negotiation prevents clipped or muted speech on `STREAM_VOICE_CALL`.
* **Local Address Book Integration:** Direct, low-latency lookups against Android's `ContactsContract` provider to distinguish personal contacts from external callers.

---

## Hardware & Validation Environment

* **Device:** Motorola Moto G60 (Android)
* **Audio Hardware:** Y10 2X Motorcycle Helmet Bluetooth Intercom
* **Core Stack:** Java, `NotificationListenerService`, `BroadcastReceiver` (`TelephonyManager`), `ContactsContract`, Android `AudioManager` (`STREAM_VOICE_CALL`), `TextToSpeech`.

---

## Roadmap (v2.0)

* **Priority Keyword Alerts:** Custom audio chimes for high-priority caller categories (medical, logistics, recruitment).
* **Local Call Telemetry:** Lightweight logging of call timestamps and response latency for personal productivity tracking.
