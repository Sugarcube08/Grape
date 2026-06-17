# Goose Codebase Architecture & Reverse-Engineering Audit

We have performed a complete soft-archaeology pass auditing the Goose Local Companion project codebase. Below is a high-level summary of the architectural insights and implementation mapping. All detailed documents are generated in the repository workspace under the `docs/` directory.

## Repository Layout & Component Map

The repository integrates a native iOS application shell (`GooseSwift`) with a portable backend core written in Rust (`Rust/core`). Integration is compiled on demand through custom build phases using the active platform toolchain.

* **Frontend App Shell (`GooseSwift/`)**:
  * Written in **Swift/SwiftUI**.
  * Manages system events, scans and pairs BLE peripherals, triggers local notifications, publishes telemetry to lock screen extensions, and handles OpenAI ChatGPT Codex streams.
  * Mapped in [docs/repository_structure.md](file:///home/sugarcube/Desktop/VGPL/Grape/docs/repository_structure.md) and cataloged file-by-file in [docs/file_catalog.md](file:///home/sugarcube/Desktop/VGPL/Grape/docs/file_catalog.md).
* **Shared Backend Core (`Rust/core/`)**:
  * Written in **Rust (Edition 2024)**.
  * Handles raw GATT packet deframing, CRC integrity checks, proprietary packet payload parsing, and calculation rollups.
  * Integrates an embedded SQLite database using statically bundled transactions.

---

## Technical Audit Findings

### 1. Bluetooth Low Energy (BLE) Reverse Engineering
* **Service Mapping**: Discovery filters identify custom WHOOP services `fd4b0001-...` for WHOOP 5.0 (Gen5) and legacy `61080001-...` for WHOOP 4.0 (Gen4).
* **Characteristic Protocols**: Suffix indicators mapping command endpoints (`...0002` to write, `...0003` to notify/respond), state events (`...0004`), real-time data streaming (`...0005`), and Memfault debug logging (`...0007`).
* **Frame Layout**: Mapped header layouts verifying frame limits, Little-Endian length counters, and payload categories.
* **Relevant Files**: [docs/ble/services.md](file:///home/sugarcube/Desktop/VGPL/Grape/docs/ble/services.md), [docs/ble/characteristics.md](file:///home/sugarcube/Desktop/VGPL/Grape/docs/ble/characteristics.md), [docs/ble/packet_format.md](file:///home/sugarcube/Desktop/VGPL/Grape/docs/ble/packet_format.md), [docs/ble/state_machine.md](file:///home/sugarcube/Desktop/VGPL/Grape/docs/ble/state_machine.md), and [docs/ble/flow.md](file:///home/sugarcube/Desktop/VGPL/Grape/docs/ble/flow.md).

### 2. Network Client Communication
* **OpenAI Codex API Integration**: Connects via HTTPS to endpoint `https://chatgpt.com/backend-api/codex/responses` to stream coach tips.
* **Authentication**: Implements OAuth2 Device Authorization Grant flow against `https://auth.openai.com/` requesting user device codes. Keys are kept out of the codebase.
* **Relevant Files**: [docs/network/authentication.md](file:///home/sugarcube/Desktop/VGPL/Grape/docs/network/authentication.md), [docs/network/endpoints.md](file:///home/sugarcube/Desktop/VGPL/Grape/docs/network/endpoints.md), and [docs/network/request_flow.md](file:///home/sugarcube/Desktop/VGPL/Grape/docs/network/request_flow.md).

### 3. Storage & Schema
* Persists telemetry inside `goose.sqlite` using transaction structures. Mapped tables include raw payloads (`raw_evidence`), decoded notifications (`ble_raw_notifications`), workout states (`activity_sessions`), and calculation aggregates (`daily_recovery_metrics`).
* **Relevant Files**: [docs/storage.md](file:///home/sugarcube/Desktop/VGPL/Grape/docs/storage.md).

### 4. SwiftUI Presentation & Views
* Coordinates Tab Bar navigations mapping Dashboard summaries, Sleep stage graphs, Cardio Load status, Coach messages, and More settings pages.
* **Relevant Files**: [docs/ui/screens.md](file:///home/sugarcube/Desktop/VGPL/Grape/docs/ui/screens.md), [docs/ui/navigation.md](file:///home/sugarcube/Desktop/VGPL/Grape/docs/ui/navigation.md), and [docs/ui/components.md](file:///home/sugarcube/Desktop/VGPL/Grape/docs/ui/components.md).

### 5. Security & Risk Analysis
* Standardizes Keychain storage operations using encryption attributes `kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly`.
* Implements safety gating for BLE commands checking read-only permissions vs state updates.
* **Relevant Files**: [docs/security.md](file:///home/sugarcube/Desktop/VGPL/Grape/docs/security.md).

### 6. Code Reusability & Cross-Platform Assessment
* Mapped components by portability: Rust library elements are fully reusable across iOS, Android, and Web platforms using FFI boundaries. Native views and delegates must be rewritten.
* **Relevant Files**: [docs/migration/reusable.md](file:///home/sugarcube/Desktop/VGPL/Grape/docs/migration/reusable.md), [docs/migration/apple_only.md](file:///home/sugarcube/Desktop/VGPL/Grape/docs/migration/apple_only.md), [docs/migration/rewrite_required.md](file:///home/sugarcube/Desktop/VGPL/Grape/docs/migration/rewrite_required.md), and [docs/migration/architecture.md](file:///home/sugarcube/Desktop/VGPL/Grape/docs/migration/architecture.md).

---

## Execution Deliverables

The complete reverse-engineered library structure has been generated:

```text
docs/
├── REPORT.md                     # Executive Summary & Architecture Report
├── repository_structure.md       # Directory-level classification
├── file_catalog.md               # Detailed scan parameters of all 247 source files
├── dependencies.md               # Rust Cargo dependencies list
├── apple_dependencies.md         # Apple SDK framework maps
├── capabilities.md               # Business capability matrix
├── security.md                   # Keychain access and command risk audit
├── storage.md                    # SQLite schemas and index descriptors
├── dead_code.md                  # Dead code list
├── ble/
│   ├── services.md               # Discoverable BLE service UUIDs
│   ├── characteristics.md        # Command/Notify characteristic suffix keys
│   ├── packet_format.md          # 8-byte frame layouts and packet type values
│   ├── state_machine.md          # Scans, subscriptions, and sync flows
│   └── flow.md                   # Realtime notify thread to persistence mapping
├── network/
│   ├── authentication.md         # OAuth2 Device Authorization sequence
│   ├── endpoints.md              # OpenAI Authorization endpoints & ChatGPT headers
│   └── request_flow.md           # Streaming prompt packet routing
├── ui/
│   ├── screens.md                # Dashboard & metric pages views list
│   ├── navigation.md             # Tab Router layout gates
│   └── components.md             # Custom charts & themes
└── migration/
    ├── reusable.md               # Portable Rust algorithms
    ├── apple_only.md             # System CoreBluetooth/HealthKit bindings
    ├── rewrite_required.md       # Flutter / Android migration alternatives
    └── architecture.md           # FFI abstraction layers diagram
```
