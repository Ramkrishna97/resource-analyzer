# Briefly

A local-first document and web page summarizer built with Java 17 and Spring Boot.

## Start

On Windows, double-click **Start Briefly.bat**. The first run downloads Gradle dependencies; the launcher waits for Spring Boot and opens `http://localhost:8080`.

Alternatively, run `gradlew.bat bootRun` and open the same URL.

## Features

- Upload PDF, DOCX, XLSX, or XLS files (30 MB maximum). Legacy binary `.doc` files should be saved as `.docx` first.
- Summarize public web pages over HTTP/HTTPS. Text, headings, tables, and up to 80 page links are extracted; media is skipped.
- Choose Light, Balanced, or Essential detail. The offline extractive summary ranks source sentences; its percentage is a target and may vary with document structure.
- Choose the built-in offline summarizer or connect to local Ollama. Install Ollama, pull a model such as `ollama pull gemma3:1b`, keep Ollama running, and select Local AI. Models are not bundled.
- Export results as TXT, DOCX, or PDF. DOCX/PDF export libraries load from a CDN when those export actions are selected.

Uploaded files are read for the request and are not stored. URL analysis fetches pages from the machine running this application. Use URLs to public pages; sites requiring login or browser-side JavaScript may not expose readable content.

## Design

`ContentExtractor<T>` separates source acquisition from the reusable `SummarizationService`. `DocumentExtractor` and `WebPageExtractor` implement independent parsing paths. `ExtractiveSummarizer` works without network access; `OllamaSummarizer` is an optional local-model adapter. `AnalyzerController` composes these services behind the browser-facing API.

- Combine up to 20 files and URLs in one run. Each source can use its own detail level; results remain labeled individually and export together.
