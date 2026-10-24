# TenancyShield 🛡️
**Your rights. Your records. Protected.**

TenancyShield is a mobile-first, offline-capable cryptographic evidence vault and move-in/move-out condition ledger for renters. It is designed to help tenants create a structured, tamper-evident record of their rental property's condition to prevent unjust security deposit deductions and disputes.

Rather than relying on scattered gallery photos, TenancyShield enforces a guided, cryptographically anchored "Move-In Condition Baseline" that can be verified and compared during the move-out inspection.

---

## 📱 User Interface: The "Warm Editorial" Design System
TenancyShield strips away cluttered, hyper-technical "crypto" aesthetics in favor of a humanized, warm, and trustworthy editorial UI.
* **Forest Green (`#263A2E`)**: Primary actions, branding, and hero cards.
* **Warm Paper (`#F7F5EF`)**: Main application background for reduced eye strain.
* **Muted Brass (`#A78352`)**: Accents, highlights, and progress indicators.
* **Charcoal Text (`#343831`)**: High-legibility typography.

The application features a clean 4-tab architecture:
1. **Home**: Dashboard, Rental Agreement details, and quick summary.
2. **Evidence**: Guided 4-step room inspection (Walls, Flooring, Ceiling, Utility Meters) with Move-In / Move-Out modes.
3. **Timeline**: Chronological, filterable cryptographic ledger of all captured evidence and documents.
4. **Settings**: Multilingual toggle, camera security preferences, and tenant profile management.

---

## ✨ Key Features

* **Offline-First Cryptographic Vault:** Capture photos, compute SHA-256 hashes, and anchor them in a local SQLite/Room hash-chain ledger entirely without an internet connection.
* **Ghost Overlay Alignment:** At move-out, use a semi-transparent overlay of your move-in photos to capture the exact same angle for accurate side-by-side comparison.
* **Hardware-Backed Signatures:** Uses the Android Keystore (TEE/StrongBox) to sign evidence payloads via ECDSA (secp256r1).
* **C2PA Tamper-Evident EXIF:** Injects C2PA provenance manifests directly into the image metadata.
* **Instant Indian Localization:** Full UI translation for English, Hindi (हिन्दी), Marathi (मराठी), Tamil (தமிழ்), Telugu (తెలుగు), Kannada (ಕನ್ನಡ), and Bengali (বাংলা).
* **Forensic PDF Export:** Generates a 2-page A4 comparative PDF report (Move-In vs. Move-Out) structured to support Bharatiya Sakshya Adhiniyam, 2023 (BSA) Section 63 certificate preparation.
* **On-Device ML Quality Gates:** Real-time CameraX and ML Kit integration to warn users about blurry photos, low lighting, or attempts to recapture images from a computer screen.
* **Witness QR Acknowledgement:** Allows landlords to scan a QR code to acknowledge receipt of the baseline, explicitly without admitting financial liability.

---

## 🛠️ Architecture & Tech Stack

TenancyShield utilizes a hybrid native-web architecture to maximize forensic hardware access while maintaining a rapid, flexible UI:

* **Frontend Engine:** Embedded Android WebView (`index.html`) using raw HTML/JS/CSS, styled with custom variables matching the Warm Editorial palette. Bidirectional JS interface (`TenancyShieldBridge`).
* **Native Android Core:** Kotlin, Jetpack Compose (for native overlays/modals).
* **Camera System:** Android CameraX API.
* **Machine Learning:** Google ML Kit Vision (Object Detection & Image Labeling) for anti-spoofing.
* **Database & Persistence:** Android Room (SQLite) for the local hash-linked `EvidenceEntity` ledger.
* **Cryptography:** `java.security` APIs (SHA-256, ECDSA), C2PA JSON standards.
* **Build System:** Gradle (Kotlin DSL).

---

## 🚀 Building and Running Locally

### Prerequisites
* Android Studio (Ladybug or newer recommended).
* Java Development Kit (JDK) 21.
* A physical Android device (Android 10+) or an Android Emulator (API Level 37 supported).
  * *Note for Windows Home users:* If using the Android Emulator, ensure the Google Android Emulator Hypervisor Driver (`aehd.sys`) is installed, as native Hyper-V is not supported.

### Build Instructions
1. Clone the repository and open the `android-app` folder in Android Studio.
2. Sync the project with Gradle files.
3. Build the Debug APK:
   
```bash
   ./gradlew assembleDebug
