// Welcome to JVisualBook
// A JShell-powered interactive notebook — like a Python notebook, but for Java.
// JVisualBook runs local Java code from `.jsh` files.
// To start JVisualBook, open a terminal in the folder containing your .jsh files
// and run:
//     java -jar jvisualbook-*.jar
// Then open your browser at: http://localhost:8080

// #

// ![JVisualBook logo](images/jvisualbook.png)

// # What is JVisualBook?

// JVisualBook turns `.jsh` files into **live, executable notebooks** displayed
// in your browser.

// Each file is a chapter made of sections. Every section can contain
// explanatory text (written as comments) and executable Java code.

// When you open a chapter, JVisualBook evaluates all code blocks from top to
// bottom in one JShell session and shows each block's output inline below it.

// You can **edit any code block** directly in the browser. After a short pause,
// the notebook is evaluated again from the beginning,
// so later blocks see the declarations created by earlier blocks.

// ## Requirements

// JVisualBook requires **Java 25 or newer** and a modern browser
// (Firefox, Chrome, or Safari released in 2023 or later).

// ## How to start the server

// Start the server from the directory that contains the `.jsh` chapters you
// want to browse. For example, from the project root after building:
// ```bash
// java -jar target/jvisualbook-*.jar
// ```
// Then open your browser at: `http://localhost:8080`

// The server scans the **current working directory** for `.jsh` files,
// and lists them as chapters in alphabetical order in the top
// navigation menu.

// Chapter names come from the file names without the `.jsh` extension.
// Use descriptive names like `01-introduction.jsh`, `02-generics.jsh`, etc.
// to keep them ordered.

// # Anatomy of a .jsh file

// A `.jsh` file is a plain JShell script with a simple convention:
//   - Lines starting with "// #" are **section titles** (rendered as Markdown).
//   - Lines starting with `"// " (note the space) are **text** (rendered as Markdown).
//   - Any other non-blank line is **code**.
//   - Blank lines separate adjacent text and code blocks.

// Here is an example:

// ```text
// // # Title
//
// // This is a text block
// // using several lines.
//
// // ## Section
//
// //This is a code comment
// IO.println("hello");    // this is code
//
// // This is another text block.
// ```

// ## Header and sections

// Leading text lines ("// ") at the very top of the file are treated as a
// file-level prologue and are **not** shown.

// You can use that prologue for a title, a short description, or instructions
// for opening the file.

// Sections start with "// #" headings. Everything between two headings belongs
// to the same section, and each section becomes one slide in Slide Mode.

// ## Your first section: Hello, World!

// The simplest code block you can write is a single expression or statement.
// Try editing the string below in your browser!

IO.println("Hello from JVisualBook!");

// # Text formatting with Markdown

// Text blocks are rendered as GitHub Flavored **Markdown**, so you can use:
// - `*italic*` and `**bold**`
// - `` `inline code` ``
// - bullet lists (like this one)
// - `> blockquotes`
// - links (`[text](url)`)
// - images (`![alt text](images/foo.png)`), restricted to local folder `images`
// - fenced code blocks (for non-executable examples)

// A blank line between comment blocks starts a new Markdown paragraph.

// ## State is shared across blocks

// A book chapter (a .jsh file) uses **one JShell session**:
// imports, classes, records, methods, and variables declared in one code block
// are visible in all subsequent blocks. This lets you build up a story step by step.

import module java.base;

var message = "Hello Java";
var version = Runtime.version().feature();

IO.println(message + " " + version);

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
// displayed in **red** below the block. Execution continues with later blocks.
// Those blocks will fail only if they depend on declarations that were not created
// because of the earlier error.

// Fix the error in the editor and the notebook re-runs automatically
// from the beginning.

// The next line intentionally shows what an error looks like:
int broken = ;

// ## Execution timeout

// To keep the notebook responsive, each chapter execution is limited to **5 seconds**.

// This means infinite loops or long-running computations will be stopped.

// The snippet below demonstrates what a timeout looks like.
// Uncomment it, wait 5 seconds, then fix it by commenting the code again.

//This snippet will time out on purpose
//while (true) {}

// # Slide Mode

// Click the **Slide Mode** button in the toolbar to present your chapter as
// a slide deck. Each section becomes one slide.

// Navigate with:
// - **← →** arrow keys (or the on-screen buttons)
// - **Escape** to exit Slide Mode

// Code blocks and their output are shown on each slide exactly as in the
// normal view, so your notebook doubles as a live presentation.

IO.println("You can run code on slides too!");

// ## Print Slides and Reload

// The **Print Slides** button renders every section as an A4-landscape page
// and opens the browser print dialog. This is ideal for creating PDF handouts
// from your chapter.

// The **Reload** button reloads the chapter from disk. Browser edits are not
// written back to the `.jsh` file, so use Reload when you want to discard those
// temporary edits or reload the file if it has been modified on disk.

// # Tips for writing good .jsh notebooks

// **Keep sections focused.** Each section (or slide) should cover one concept.
// Aim for one short code block per section where possible.

// **Order matters.** Blocks run top-to-bottom in one shared JShell session,
// so define things before you use them.

// **Preview classes and Java features.** The JShell runner starts with
// `--enable-preview` and uses the current JDK feature version, so you can use
// Java preview features supported by your JDK.

// **5 seconds timeout**. Keep your snippets short and focused on illustrating
// a concept rather than running heavy computations.

// **File naming.** Chapter names in the UI come directly from the `.jsh`
// file name (without the extension) and are sorted alphabetically.

// # Security model

// JVisualBook is designed for trusted local use.

// Code is executed by JShell with the same permissions as the Java process.
// It can read and write files, access the network, inspect environment variables,
// consume CPU and memory.

// # Now, it's your turn!

IO.println("""
  Create your own .jsh notebook.
  Have fun!
  """);