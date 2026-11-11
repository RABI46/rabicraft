# RabiCraft for Android

<p align="center">
  <img src="assets/app-icon/hicolor/256x256/apps/org.rabicraft.rabicraft.png" width="96" height="96" alt="RabiCraft icon">
</p>

**RabiCraft** is the open-source PDF workbench for Android, built natively with Kotlin and Jetpack Compose. Read, organize, annotate, fill & sign, combine, split, and protect PDFs in a fast, privacy-first mobile application.

---

## Highlights

- **Faithful Reading:** Native Android PDF rendering with deep zoom, continuous scroll, single-page view, and two-up facing pages mode.
- **Reading Themes:** Default light theme, RabiCraft dark mode, sepia tone for reading comfort, and high-contrast inverted mode.
- **Page Workbench (Organize):** Visual card table to reorder, rotate 90°, duplicate, delete, and extract pages.
- **Annotate & Markup:** Freehand ink pen, smart text highlighter, shape boxes, and custom stroke colors.
- **Fill & Sign:** Interactive AcroForm field editing and digital signature pad (draw or type signatures).
- **Combine & Split:** Merge multiple documents or extract page ranges into new PDF files.
- **Find in Document:** Fast in-document text search with match counts and step-through navigation.
- **Threaded Comments:** Add reviews, notes, and threaded replies with resolution tracking.
- **Document Security & Standards:** Password protection, granular permissions, metadata sanitizer, and ISO 32000-2 conformance audit.
- **Zero Telemetry:** 100% offline and private to your device. No cloud account or external servers required.

---

## Architecture & Technology

- **Framework:** Modern Android Jetpack Compose with Material 3 design system.
- **Rendering:** Hardware-accelerated native `android.graphics.pdf.PdfRenderer` and `android.graphics.pdf.PdfDocument`.
- **State Management:** MVVM with Coroutines and `MutableStateFlow` / `collectAsStateWithLifecycle`.
- **Typography:** Bundled Inter and JetBrains Mono fonts.

## License

Dual-licensed under MIT OR Apache-2.0.
