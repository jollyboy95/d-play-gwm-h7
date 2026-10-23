# DiPlay GWM Adapter

An integration layer that adds **GWM / Haval** (Lemon · Harman OS · iFlytek HiLife)
support to [DiPlay](https://diplay.org), focused on getting projected content onto
the **instrument cluster / HUD** of GWM vehicles rather than only the centre screen.

Primary target for development and testing: **2026 Haval H7 Vanta (B11, Australian
market)**, NOBO IN9.0-OS head unit (Android 9, Qualcomm SA8155).

Licensed **GPL-3.0-or-later** to match DiPlay, so the module can be upstreamed back
into the project.

---

## Scope — read this first

This repository is the **GWM vehicle-integration layer only**. It handles three
things that are specific to GWM hardware and that sit *above* DiPlay's existing
projection stack:

1. **Display routing** — enumerating the car's logical displays and placing a
   rendered surface on the cluster / HUD instead of (or alongside) the main screen.
2. **Cluster/HUD rendering** — a lightweight, non-interactive renderer (speed, fuel /
   range, optional small turn indication) suitable for a driver display.
3. **Steering-wheel input** — read-only observation of the GWM adapter key stream so
   the host app can later map buttons to actions.

**This repository does not contain, reproduce, modify, or depend on any device-
authentication material.** DiPlay's upstream handles its own phone-projection and
authentication; this adapter rides on top of whatever the host build already
provides and never touches that layer. No MFi certificates, authentication keys,
cert-chip code, or protocol-handshake logic are included here, by design. See
[`NOTICE.md`](NOTICE.md).

Everything here operates on hardware the developer owns, for interoperability and
to feed a tested GWM port back to an open-source (GPL-3.0) project.

---

## Status

Early scaffold. The display IDs below are **placeholders that must be enumerated on
the actual vehicle** — SurfaceFlinger physical tokens and logical display IDs differ
per head unit and per boot. Nothing here is confirmed on a car yet. Treat every
display ID and every steering key code as **unverified until logged on the H7**.

| Area | State |
|------|-------|
| Display enumeration | Implemented, needs on-car verification |
| Cluster overlay render | Implemented (parked-test only) |
| Dial / HUD renderer | Minimal reference renderer |
| Steering-key observer | Read-only parser, codes unverified |
| Host-app integration | Documented, not yet wired to a DiPlay build |

---

## Safety rules (non-negotiable)

- Validate all driver-display behaviour **while parked**.
- Never suppress or overdraw factory speed, gear, or warning/telltale content.
- Do not modify the factory cluster service, automotive permissions, or system config.
- The cluster renderer is **non-interactive** (`FLAG_NOT_TOUCHABLE`,
  `FLAG_NOT_FOCUSABLE`) so it cannot capture driver input.

---

## Layout

```
dplay-gwm-adapter/
├── LICENSE                     GPL-3.0-or-later (verbatim)
├── NOTICE.md                   Scope + what is deliberately excluded
├── docs/
│   ├── H7_DISPLAY_ARCHITECTURE.md
│   ├── STEERING_KEYS.md
│   └── INTEGRATION.md
└── gwm-adapter/                Android library module
    └── src/main/
        ├── AndroidManifest.xml
        └── java/org/dplay/gwm/
            ├── GwmAdapterConfig.java
            ├── GwmDisplayAdapter.java
            ├── ClusterOverlayRenderer.java
            ├── ClusterDialView.java
            └── GwmSteeringKeyObserver.java
```

## Acknowledgements

- DiPlay / Worldjesse DiPlay (GPL-3.0) — the host projection app this plugs into.
- Display-routing and overlay approach adapted from the author's own ClusterProbe
  work (`jollyboy95/Cluster-test-new`).
