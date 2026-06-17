# Dependency Graph & Audit

This document audits the Rust dependency hierarchy resolved from the original codebase.

## Resolved Dependency Tree (`cargo tree`)

```text
grape-core v0.1.0
├── crc32fast v1.5.0
│   └── cfg-if v1.0.4
├── hex v0.4.3
├── rusqlite v0.37.0
│   ├── bitflags v2.11.1
│   ├── fallible-iterator v0.3.0
│   ├── fallible-streaming-iterator v0.1.9
│   ├── hashlink v0.10.0
│   │   └── hashbrown v0.15.5
│   │       └── foldhash v0.1.5
│   ├── libsqlite3-sys v0.35.0
│   │   [build-dependencies]
│   │   ├── cc v1.2.62
│   │   │   ├── find-msvc-tools v0.1.9
│   │   │   ├── jobserver v0.1.34
│   │   │   │   └── libc v0.2.186
│   │   │   ├── libc v0.2.186
│   │   │   └── shlex v1.3.0
│   │   ├── pkg-config v0.3.33
│   │   └── vcpkg v0.2.15
│   └── smallvec v1.15.1
├── serde v1.0.228
│   ├── serde_core v1.0.228
│   └── serde_derive v1.0.228 (proc-macro)
│       ├── proc-macro2 v1.0.106
│       │   └── unicode-ident v1.0.24
│       ├── quote v1.0.45
│       │   └── proc-macro2 v1.0.106 (*)
│       └── syn v2.0.117
│           ├── proc-macro2 v1.0.106 (*)
│           ├── quote v1.0.45 (*)
│           └── unicode-ident v1.0.24
├── serde_json v1.0.150
│   ├── itoa v1.0.18
│   ├── memchr v2.8.1
│   ├── serde_core v1.0.228
│   └── zmij v1.0.21
├── sha2 v0.10.9
│   ├── cfg-if v1.0.4
│   ├── cpufeatures v0.2.17
│   └── digest v0.10.7
│       ├── block-buffer v0.10.4
│       │   └── generic-array v0.14.7
│       │       └── typenum v1.20.0
│       │       [build-dependencies]
│       │       └── version_check v0.9.5
│       ├── crypto-common v0.1.7
│       │   ├── generic-array v0.14.7 (*)
│       │   └── typenum v1.20.0
│       └── subtle v2.6.1
├── thiserror v2.0.18
│   └── thiserror-impl v2.0.18 (proc-macro)
│       ├── proc-macro2 v1.0.106 (*)
│       ├── quote v1.0.45 (*)
│       └── syn v2.0.117 (*)
├── tungstenite v0.28.0
│   ├── bytes v1.11.1
│   ├── data-encoding v2.11.0
│   ├── http v1.4.1
│   │   ├── bytes v1.11.1
│   │   └── itoa v1.0.18
│   ├── httparse v1.10.1
│   ├── log v0.4.30
│   ├── rand v0.9.4
│   │   ├── rand_chacha v0.9.0
│   │   │   ├── ppv-lite86 v0.2.21
│   │   │   │   └── zerocopy v0.8.49
│   │   │   └── rand_core v0.9.5
│   │   │       └── getrandom v0.3.4
│   │   │           ├── cfg-if v1.0.4
│   │   │           └── libc v0.2.186
│   │   └── rand_core v0.9.5 (*)
│   ├── sha1 v0.10.6
│   │   ├── cfg-if v1.0.4
│   │   ├── cpufeatures v0.2.17
│   │   └── digest v0.10.7 (*)
│   ├── thiserror v2.0.18 (*)
│   └── utf-8 v0.7.6
└── zip v0.6.6
    ├── aes v0.8.4
    │   ├── cfg-if v1.0.4
    │   ├── cipher v0.4.4
    │   │   ├── crypto-common v0.1.7 (*)
    │   │   └── inout v0.1.4
    │   │       └── generic-array v0.14.7 (*)
    │   └── cpufeatures v0.2.17
    ├── byteorder v1.5.0
    ├── bzip2 v0.4.4
    │   ├── bzip2-sys v0.1.13+1.0.8
    │   │   [build-dependencies]
    │   │   ├── cc v1.2.62 (*)
    │   │   └── pkg-config v0.3.33
    │   └── libc v0.2.186
    ├── constant_time_eq v0.1.5
    ├── crc32fast v1.5.0 (*)
    ├── flate2 v1.1.9
    │   ├── crc32fast v1.5.0 (*)
    │   └── miniz_oxide v0.8.9
    │       ├── adler2 v2.0.1
    │       └── simd-adler32 v0.3.9
    ├── hmac v0.12.1
    │   └── digest v0.10.7 (*)
    ├── pbkdf2 v0.11.0
    │   ├── digest v0.10.7 (*)
    │   ├── hmac v0.12.1 (*)
    │   ├── password-hash v0.4.2
    │   │   ├── base64ct v1.8.3
    │   │   ├── rand_core v0.6.4
    │   │   └── subtle v2.6.1
    │   └── sha2 v0.10.9 (*)
    ├── sha1 v0.10.6 (*)
    ├── time v0.3.47
    │   ├── deranged v0.5.8
    │   │   └── powerfmt v0.2.0
    │   ├── num-conv v0.2.2
    │   ├── powerfmt v0.2.0
    │   └── time-core v0.1.8
    └── zstd v0.11.2+zstd.1.5.2
        └── zstd-safe v5.0.2+zstd.1.5.2
            ├── libc v0.2.186
            └── zstd-sys v2.0.16+zstd.1.5.7
                [build-dependencies]
                ├── cc v1.2.62 (*)
                └── pkg-config v0.3.33
```

## Dependency Categorization

| Crate Name | Status | Grouping | Reason for Status |
| ---------- | ------ | -------- | ----------------- |
| `crc32fast` | **KEEP** | Core utilities | Necessary for WHOOP packet header CRC verification. |
| `hex` | **KEEP** | Core utilities | Converts raw BLE payload sequences to string arrays. |
| `rusqlite` | **KEEP** | Core utilities | Local persistence database driver. Built statically. |
| `serde` | **KEEP** | Serialization | Structural macro framework for mapping JSON context. |
| `serde_json` | **KEEP** | Serialization | Compiles request payloads into string bridges. |
| `sha2` | **KEEP** | Cryptography | Identifies and deduplicates recurring packets. |
| `thiserror` | **KEEP** | Error handling | Simplifies custom error configurations. |
| `tungstenite` | **REMOVE** | Networking | WebSocket client/server mockup. Not needed for production app. |
| `zip` | **KEEP** | Backups/Export | Compresses raw evidence directories for file sharing. |
| `tempfile` | **KEEP** | Dev dependency | Creates sandbox stores during unit test executions. |
| `uniffi` | **ANDROID SPECIFIC** | FFI Bindings | (To be added) Generates Kotlin interfaces. |
| `uniffi_bindgen` | **ANDROID SPECIFIC** | Build Tools | (To be added) Generates JNI shared link libraries. |
