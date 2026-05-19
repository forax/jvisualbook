package com.github.forax.jvisualbook;

import java.util.List;
import java.util.Objects;

/// Defines the core domain model for JVisualBook
/// Those objects are sent back and forth between the UI and the backend.
///
/// The model describes the full lifecycle of a chapter: from its discovery as a
/// [Chapter], through its parsed representation as a [Document] composed of
/// [Section]s and [Content] blocks, to code execution via [Code],
/// [Snippet]s, and their resulting [Execution] and [Evaluation]s.
///
/// All classes are immutable, thus thread-safe.
public interface Model {

  /// Represents a discovered chapter, identified by its name.
  ///
  /// A chapter corresponds to a `.jsh` file on the disk.
  /// The name is the filename without the `.jsh` extension.
  ///
  /// @param name the chapter name
  record Chapter(String name) {
    /// Create a new Chapter
    /// @throws NullPointerException if `name` is `null`
    public Chapter {
      Objects.requireNonNull(name);
    }
  }

  /// Represents a parsed `.jsh` file as an ordered list of sections.
  ///
  /// A document is produced by [DocumentParser] and reflects
  /// the structure of the source file.
  ///
  /// @param sections the ordered list of sections in this document
  record Document(List<Section> sections) {
    /// Create a new Document
    /// @throws NullPointerException if `sections` is `null`
    public Document {
      sections = List.copyOf(sections);
    }
  }

  /// Represents a named section within a [Document].
  ///
  /// A section begins with a heading line of the form `// # ...`
  /// in the source file.
  /// Its contents are an ordered sequence of [Content] blocks
  /// (text and code) that follow the heading.
  /// Each section maps to one slide in slide mode and print mode.
  ///
  /// @param title    the section heading text (Markdown is supported)
  ///                 may be empty for synthetic sections created from headingless content
  /// @param contents the ordered list of content blocks in this section
  record Section(String title, List<Content> contents) {
    /// Create a new Section
    /// @throws NullPointerException if `title` or `contents` is `null`
    public Section {
      Objects.requireNonNull(title);
      contents = List.copyOf(contents);
    }
  }

  /// Represents a single block of content within a [Section].
  ///
  /// Content is either a text block ([Kind#TEXT]) rendered as Markdown,
  /// or a Java code block ([Kind#CODE]) rendered using the monaco editor.
  ///
  /// @param kind the type of this content block
  /// @param text the raw text of this block
  record Content(Kind kind, String text) {

    /// Discriminates between the two kinds of content blocks in a section.
    /// Note: there is no kind OUTPUT, which is defined in the UI part
    /// but does not exist in the backend part (which reflects a document).
    public enum Kind {
      /// A text block whose text is rendered as Markdown.
      TEXT,
      /// A Java code block whose text is submitted to JShell for execution.
      CODE
    }

    /// Create a new Content
    /// @throws NullPointerException if `kind` or `text` is `null`
    public Content {
      Objects.requireNonNull(kind);
      Objects.requireNonNull(text);
    }
  }

  /// Represents a unit of Java code submitted for execution,
  /// consisting of one or more [Snippet]s.
  ///
  /// Snippets are executed sequentially in a single JShell session.
  /// Declarations made in an earlier snippet are visible to later ones.
  ///
  /// @param snippets the ordered list of snippets to evaluate
  record Code(List<Snippet> snippets) {
    /// Create a new Code
    /// @throws NullPointerException if `snippets` is `null`
    public Code {
      snippets = List.copyOf(snippets);
    }
  }

  /// Represents a single Java source fragment to be evaluated by JShell.
  ///
  /// A snippet may contain one or more Java statements, declarations, or expressions.
  /// Its output and error streams are captured and returned as part of the [Evaluation].
  ///
  /// @param code the Java source text
  record Snippet(String code) {
    /// Create a new Snippet
    /// @throws NullPointerException if `code` is `null`
    public Snippet {
      Objects.requireNonNull(code);
    }
  }

  /// Represents the result of executing a [Code] object,
  /// containing one [Evaluation] per submitted [Snippet], in order.
  ///
  /// @param evaluations the ordered list of per-snippet evaluation results; never `null`
  record Execution(List<Evaluation> evaluations) {
    /// Create a new Execution
    /// @throws NullPointerException if `evaluations` is `null`
    public Execution {
      evaluations = List.copyOf(evaluations);
    }
  }

  /// Represents the outcome of evaluating a single [Snippet].
  ///
  /// On success, `text` contains any captured standard output and standard error
  /// produced during evaluation. On error, `text` contains a diagnostic message
  /// describing the compile-time or runtime failure.
  ///
  /// @param status the outcome of the evaluation
  /// @param text the captured output or error message; may be empty
  record Evaluation(Status status, String text) {

    /// Discriminates between a successful and a failed snippet evaluation.
    public enum Status {
      /// The snippet was rejected by the compiler or threw an exception at runtime.
      ERROR,
      /// The snippet was compiled and executed without error.
      SUCCESS
    }

    /// Create a new Evaluation
    /// @throws NullPointerException if `status` or `text` is `null`
    public Evaluation {
      Objects.requireNonNull(status);
      Objects.requireNonNull(text);
    }
  }
}