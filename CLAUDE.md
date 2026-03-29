# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

A Spring Boot 3.x auto-configuration starter that wraps Alibaba EasyExcel to provide Excel import/export capabilities. Consumer projects add this as a Maven dependency and get `FileEngine` auto-injected. All code and comments are in Chinese.

**Stack:** Java 17, Spring Boot 3.2.x, EasyExcel 3.3.3, Jakarta Validation, Maven

## Build Commands

```bash
mvn clean install          # Build and install to local repo
mvn test                   # Run tests
mvn test -Dtest=TestBiz    # Run a single test class
mvn package                # Package without install
```

## Architecture

### Request Flow

```
Consumer App → FileEngine (public API)
  ├── Import: FileEngine → FileParser strategy → ExcelFileParser → EasyExcelReaderUtil → SmartExcelListener
  └── Export: FileEngine → DownloadService (default interface methods) → EasyExcel writer
```

### Key Components

- **`FileEngine`** (`core/`): Single public API entry point. Injected by consumers via Spring DI. Delegates import to `FileParser` strategy chain, implements `DownloadService` for export.
- **`DownloadService`** (`core/`): Interface with default methods for all export variants (template, standard, paginated, dynamic headers). `FileEngine` implements this.
- **`FileParser` / `ExcelFileParser`** (`strategy/`): Strategy pattern for file format support. `ExcelFileParser` reads `@FileModel` annotation config and delegates to `EasyExcelReaderUtil`.
- **`SmartExcelListener`** (`util/`): Core EasyExcel `AnalysisEventListener` implementation. Handles batch processing, merged cell flattening, JSR-303 validation, sheet metadata injection, and error routing.
- **`EasyExcelReaderUtil`** (`util/`): Orchestrates the read pipeline. Uses NIO.2 temp files. When `enableMerge=true`, performs two-pass reading (pass 1 collects merge regions, pass 2 processes data).
- **`ExcelValidationHandler`** (`strategy/`): Functional interface for consumer-side error handling. Provides hooks for validation failures and type conversion exceptions.
- **`GearFileAutoConfiguration`** (`config/`): Auto-configuration registered via `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`. Creates `FileEngine` bean with `@ConditionalOnMissingBean`.

### Custom Annotations (`annotation/`)

- `@FileModel` (type-level): Configures `showName`, `headRowNumber`, `enableMerge`, `batchSize`
- `@ExcelSheetNo` (field-level): Auto-injects current sheet number during import
- `@ExcelSheetName` (field-level): Auto-injects current sheet name during import

### Design Decisions

- **Reflection caching** uses Spring's `ConcurrentReferenceHashMap` (weak references) to avoid Metaspace leaks from static `Map<Class<?>, ...>` patterns.
- **Merged cell handling** is opt-in via `@FileModel(enableMerge=true)` because it requires a two-pass read that doubles I/O cost.
- **Validation errors** resolve Java field names back to Chinese Excel header names for user-facing messages by cross-referencing `colIndexToFieldMap` with EasyExcel's `headMap`.
- All export methods set response headers (Content-Disposition, Content-Type) internally; callers only pass `HttpServletResponse`.

## Project Structure

```
src/main/java/com/gear/file/
├── annotation/    # @FileModel, @ExcelSheetNo, @ExcelSheetName
├── config/        # GearFileAutoConfiguration
├── core/          # FileEngine, DownloadService
├── exception/     # GearFileException
├── strategy/      # FileParser interface, ExcelValidationHandler, impl/ExcelFileParser
└── util/          # SmartExcelListener, EasyExcelReaderUtil
```

## Adding a New File Format

Implement `FileParser`, add `@Component`, and provide `support(suffix)` + `parse(...)`. The strategy chain in `FileEngine` auto-discovers all `FileParser` beans.
