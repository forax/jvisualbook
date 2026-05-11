// Welcome to JVisualBook
// A JShell-powered interactive notebook — like a Python notebook, but for Java.
// To start JVisualBook, open a terminal in the folder containing your .jsh files
// and run:
//     java -jar jvisualbook-*.jar
// Then open your browser at: http://localhost:8080

// # What is JVisualBook?

// JVisualBook turns `.jsh` files into **live, executable notebooks** displayed
// in your browser. Each file is a chapter made of sections. Every section can
// contain explanatory text (written as comments) and executable Java code.

// When you open a chapter, all code blocks are automatically run top-to-bottom
// and their output is shown inline below each block.

// You can **edit any code block** directly in the browser and the notebook will
// re-run it (and all subsequent blocks) after a short pause.

// # How to start the server

// From the project root, run the fat JAR produced by `mvn package`:
// ```bash
// java -jar target/jvisualbook-*.jar
// ```
// Then open your browser at: `http://localhost:8080`

// The server scans the **current working directory** for `.jsh` files and
// lists them as chapters in the top navigation bar.
// Place your `.jsh` files in the same directory you launch the JAR from.

// # Anatomy of a .jsh file

// A `.jsh` file is a plain JShell script with a simple convention:
//   - Lines starting with `// #` are **section titles** (headings in Markdown).
//   - Lines starting with `// ` (note the space) are **text** (Markdown).
//   - Any other non-blank line is **code**.
//   - Blank lines act as separators.

// ## Header and sections

// The very first block of `// ` lines at the top of the file
// (before the first blank line) is treated as a file-level header and is **not** shown.
// It is a good place for a title and a short description, like this file.

// Sections starts with `// #` headings.
// Everything between two headings belongs to the same section (one slide in Slide Mode).

// ## Your first section: Hello, World!

// The simplest code block you can write is a single expression or statement.
// Try editing the string below in your browser!

IO.println("Hello from JVisualBook!");

// ## State are shared across blocks

// Within a chapter, the JShell session is **shared**: imports, classes, methods,
// and variables are declared in one code block are visible in all subsequent blocks.
// This lets you build up a story step by step.

import module java.base;

var message = "JVisualBook";
var year    = 2025;

IO.println("Welcome to " + message + " (" + year + ")");

// ## Multiple statements in one block

// A single code block can contain as many statements as you like.
// All output produced by the block is collected and shown together.

for (var i = 1; i <= 5; i++) {
  IO.println("Step " + i);
}

// ## Defining methods and classes

// You can define methods and classes exactly as you would in a JShell session.
// They become available to every block that comes after them.

int factorial(int n) {
  return n <= 1 ? 1 : n * factorial(n - 1);
}

IO.println("10! = " + factorial(10));

// ## Using the result in a later block

// Because `factorial` was defined above, we can call it here.

var results = new ArrayList<String>();
for (var n = 1; n <= 6; n++) {
  results.add(n + "! = " + factorial(n));
}
IO.println(String.join("\n", results));

// ## Error handling

// If a code block contains a syntax or runtime error, the error message is
// displayed in **red** below the block. Later blocks are still executed independently.
// An error in one block does not abort the whole chapter.

// Fix the error in the editor and the notebook re-runs automatically.

// The next line shows what an error looks like:
int broken = ;

// # Text formatting with Markdown

// Text lines (`// `) are rendered as **Markdown**, so you can use:
// - `*italic*` and `**bold**`
// - `` `inline code` ``
// - Bullet lists (like this one)
// - `> blockquotes`
// - Fenced code blocks (for non-executable examples)

// Keep each paragraph as a consecutive run of `// ` lines.

// A blank line between comment blocks starts a new Markdown paragraph.

// # User interface

// ## Slide Mode

// Click the **Slide Mode** button in the toolbar to present your chapter as
// a slide deck. Each section becomes one slide.

// Navigate with:
// - **← →** arrow keys (or the on-screen buttons)
// - **Escape** to exit Slide Mode

// Code blocks and their output are shown on each slide exactly as in the
// normal view, so your notebook doubles as a live presentation.

IO.println("You can run code on slides too!");

// ## Print Slides

// The **Print Slides** button renders every section as an A4-landscape page
// and opens the browser print dialog. This is ideal for creating PDF handouts
// from your chapter.

// # Tips for writing good .jsh notebooks

// **Keep sections focused.** Each section (slide) should cover one concept.
// Aim for one short code block per section where possible.

// **Order matters.** Blocks run top-to-bottom in one shared JShell session,
// so define things before you use them.

// **Preview classes and Java features.** The JShell runner starts with
// `--enable-preview` and uses the current JDK version, so you can use the
// latest Java preview features (e.g., value classes, pattern matching)
// if your JDK supports them.

// **File naming.** Chapter names in the UI come directly from the `.jsh`
// file name (without the extension). Use descriptive names like
// `01-introduction.jsh`, `02-generics.jsh`, etc. to keep them ordered.

// # Now, it's your turn!'

IO.println("""
  Create your own .jsh notebook.
  Have fun!
  """);
