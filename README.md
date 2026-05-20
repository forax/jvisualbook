# JVisualBook
JVisualBook is a local, browser-based notebook for JShell files (Java interactive REPL files).

It lets you open a `.jsh` chapter that mixes Markdown-style explanations with executable Java snippets.
View it as a document or slide deck, edit snippets in the browser, and auto-run the whole chapter through JShell.

> **⚠️ Warning:** JVisualBook executes Java code from notebooks. Treat it as a **local trusted-code tool**.
> Do not expose it to untrusted users or the public internet.

## Features

- Parse `.jsh` files into chapters made of sections, text, and Java code blocks.
- Render text as Markdown in the web UI.
- Edit Java snippets with Monaco Editor (the one used by Visual Studio Code).
- Re-run all snippets through JShell after edits so later snippets can depend on earlier declarations.
- Display captured standard output and standard error inline, with errors highlighted in red.
- Switch to a full-screen slide view.
- Print the rendered slides to PDF.

## Requirements

**JDK 25 or newer with JShell support.**
JShell evaluation enables preview features using the current runtime feature version.

A modern **browser from 2023+** to run the web UI.

## Quick start

Build the jar first (see [Build](#build)),
or use a [prebuild release](https://github.com/forax/jvisualbook/releases/latest) from Github,
then run it from the folder containing your `.jsh` files:

```bash
java -jar jvisualbook-1.1.3.jar
```

Then open:

```text
http://localhost:8080
```

## Writing chapters

JVisualBook discovers chapters by listing `.jsh` files in the directory where the server is started.
Chapter names are the file names without the `.jsh` extension, sorted alphabetically.

A chapter is composed of sections. Each section starts with a heading and alternates text blocks and code.

Comment lines before the first section heading are dropped during parsing.
You can use this "prologue" for anything that should not appear in the rendered document,
such as instructions on how to open the file in JVisualBook.

### Section headings

A section starts with a comment line beginning with `// #`:

```java
// # My section title
```

The text after `// ` becomes the section title, so Markdown headings work naturally:

```java
// ## A subsection title
```

Each section becomes one slide in slide mode and print mode.

### Text

Text lines begin with `// ` and are rendered as Markdown:

```java
// This is **Markdown** prose.
// - Bullet one
// - Bullet two
```

### Code

Non-comment, non-blank lines are treated as Java code and executed by JShell:

```java
var name = "Java";
IO.println("Hello " + name);
```

Output and errors are displayed inline below each code block. Errors are highlighted in red.

When the document runs, JVisualBook executes all code blocks in order using one JShell session,
so later code can use imports, classes, records, methods and variables declared earlier.

### Execution timeout

The entire chapter execution is limited to **5 seconds**. If execution has not completed within
that time, JShell is forcibly stopped and every snippet is marked as an error:

```
Error: execution timed out after 5 seconds
```

This means infinite loops or long-running computations will be interrupted. Keep snippets
short and side-effect-free — they are meant to illustrate concepts, not run benchmarks.

### Example chapter

```java
// This prologue is dropped and will not appear in the rendered document.

// # Hello JVisualBook

// This is text in **Markdown** format.

var name = "JVisualBook";
IO.println("Hello " + name);

// ## Reusing earlier declarations
// The variables from the previous block are available here.

IO.println(name.toUpperCase());
```

Any edit in any code block triggers re-evaluation of the whole chapter after 500 ms.

## Security model

JVisualBook assumes a trusted local user:

- Submitted snippets are real Java code evaluated by JShell.
- Snippets can consume arbitrary CPU and memory up to the 5-second execution timeout.
- Snippets may access files, environment variables, and network resources available to the Java process.
- The app must not be deployed as a shared or public-facing service.