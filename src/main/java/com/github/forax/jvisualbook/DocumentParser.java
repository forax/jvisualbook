package com.github.forax.jvisualbook;

import com.github.forax.jvisualbook.Model.Content;
import com.github.forax.jvisualbook.Model.Document;
import com.github.forax.jvisualbook.Model.Section;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;

/// Parses `.jsh` files into a [Document] model.
///
/// A `.jsh` file is a JShell script that interleaves Java code with
/// Markdown-style comments. `DocumentParser` recognises three kinds of lines:
///
/// - **Section headings**: comment lines starting with `// #`, which open a
///   new [Section]. The text after `// ` becomes the section title, so standard
///   Markdown heading syntax (`#`, `##`, etc.) works naturally.
/// - **Text lines**: comment lines starting with `// ` (note the space after `//`),
///   collected into [Content] blocks of kind [Content.Kind#TEXT] and rendered
///   as Markdown in the UI.
/// - **Code lines**: all other non-blank lines, collected into [Content] blocks
///   of kind [Content.Kind#CODE] and executed by JShell.
///
/// Blank lines act as separators between text and code blocks but are otherwise
/// ignored and never appear in the resulting [Document].
///
/// ## Prologue stripping
///
/// Any text-only lines that appear _before_ the first blank line are treated as
/// a prologue and are silently dropped. This lets authors add instructions for
/// opening the file without those instructions showing up in the rendered view.
///
/// ## Synthetic sections
///
/// If code or text appears before the first explicit section heading, the parser
/// wraps it in a synthetic [Section] whose title is the empty string.
///
/// ## Example input
///
/// ```java
/// // This prologue line is dropped.
///
/// // # Hello JVisualBook
/// // This is **Markdown** text.
///
/// var name = "JVisualBook";
/// IO.println("Hello " + name);
///
/// // ## Reusing earlier declarations
/// IO.println(name.toUpperCase());
/// ```
///
/// The snippet above produces a [Document] with two sections:
///
/// 1. _"# Hello JVisualBook"_, one TEXT block followed by one CODE block.
/// 2. _"## Reusing earlier declarations"_, one CODE block.
///
/// @see Document
/// @see Model
public final class DocumentParser {

  /// This class is a utility class
  private DocumentParser() {
    throw new AssertionError();
  }

  /// Classifies each line of a `.jsh` source file.
  ///
  /// The four values map directly to the structural roles a line can play:
  ///
  /// | Value     | Pattern                  | Role                          |
  /// |-----------|--------------------------|-------------------------------|
  /// | `SECTION` | starts with `// #`       | opens new [Section]           |
  /// | `TEXT`    | starts with `// `        | contributes to a TEXT block   |
  /// | `BLANK`   | blank or whitespace-only | separates blocks              |
  /// | `CODE`    | anything else            | contributes to a CODE block   |
  private enum LineKind {
    TEXT,
    SECTION,
    BLANK,
    CODE,
    ;

    /// Strips the `// ` prefix from `TEXT` and `SECTION` lines.
    String clean(String line) {
      return switch(this) {
        case BLANK, CODE -> line;
        case TEXT, SECTION -> line.substring(3);
      };
    }

    /// Classifies a single source line.
    static LineKind kind(String line) {
      if (line.startsWith("// #")) {
        return SECTION;
      }
      if (line.startsWith("// ")) {
        return TEXT;
      }
      if (line.isBlank()) {
        return BLANK;
      }
      return CODE;
    }

    /// Returns a predicate that tests whether a line has the given [LineKind].
    static Predicate<String> is(LineKind kind) {
      return line -> kind(line) == kind;
    }
  }

  /// Callback interface used by [#transformTo] to emit structural events while
  /// scanning a list of source lines.
  ///
  /// Implementors override only the events they care about; all methods have
  /// empty default implementations except [#line], which is abstract.
  ///
  /// The event sequence for a well-formed document is:
  ///
  /// ```
  /// startDocument()
  ///   ( startSection() line(SECTION,...)
  ///       ( startText()  line(TEXT,...)+ endText()
  ///       | startCode()  line(CODE,...)+ endCode()
  ///       )*
  ///     endSection()
  ///   )*
  /// endDocument()
  /// ```
  private interface EventHandler {

    /// Called once before any line is processed.
    default void startDocument() { /*empty*/ }

    /// Called once after all lines have been processed.
    default void endDocument() { /*empty*/ }

    /// Dispatches to [#startCode], [#startText], or [#startSection] based on `kind`.
    ///
    /// @param kind the kind of block that is starting; never `BLANK`
    default void start(LineKind kind) {
      switch(kind) {
        case null -> throw null;   // Make the switch exhaustive
        case CODE -> startCode();
        case TEXT -> startText();
        case SECTION -> startSection();
        case BLANK -> throw new AssertionError();
      }
    }

    /// Dispatches to [#endCode], [#endText], or [#endSection] based on `kind`.
    ///
    /// @param kind the kind of block that is ending; never `BLANK`
    default void end(LineKind kind) {
      switch(kind) {
        case null -> throw null;   // Make the switch exhaustive
        case CODE -> endCode();
        case TEXT -> endText();
        case SECTION -> endSection();
        case BLANK -> throw new AssertionError();
      }
    }

    /// Called when a CODE block begins.
    default void startCode() { /*empty*/ }

    /// Called when a CODE block ends.
    default void endCode() { /*empty*/ }

    /// Called when a TEXT block begins.
    default void startText() { /*empty*/ }

    /// Called when a TEXT block ends.
    default void endText() { /*empty*/ }

    /// Called when a SECTION block begins (i.e., a `// #` heading line is found).
    default void startSection() { /*empty*/ }

    /// Called when a SECTION block ends (i.e. the next heading or EOF is reached).
    default void endSection() { /*empty*/ }

    /// Called for every line, including blank ones.
    ///
    /// @param kind the kind of the line
    /// @param line the line content
    void line(LineKind kind, String line);
  }

  /// Scans `lines` and calls structural events on `handler`.
  ///
  /// This is the core parsing engine. It maintains a small state machine
  /// that emits `start`/`end` events.
  ///
  /// If there is no first section, a synthetic section with an empty title is created.
  ///
  /// @param lines   the source lines to scan, in order
  /// @param handler the event receiver; must not be `null`
  private static void transformTo(List<String> lines, EventHandler handler) {
    handler.startDocument();

    var inside = LineKind.BLANK;
    var insideSection = false;
    for (var line : lines) {
      var kind = LineKind.kind(line);

      inside = switch (kind) {
        case BLANK -> {
          if (inside == LineKind.CODE || inside == LineKind.TEXT) {
            handler.end(inside);
          }
          yield kind;
        }
        case SECTION -> {
          if (inside == LineKind.CODE || inside == LineKind.TEXT) {
            handler.end(inside);
          }
          if (insideSection) {
            handler.end(LineKind.SECTION);
          }
          handler.start(LineKind.SECTION);
          insideSection = true;
          yield LineKind.BLANK;
        }
        case TEXT, CODE -> {
          if (!insideSection) {
            handler.start(LineKind.SECTION);
            handler.line(LineKind.SECTION, "");
            insideSection = true;
          }
          var opposite = kind == LineKind.CODE ? LineKind.TEXT : LineKind.CODE;
          if (inside == opposite) {
            handler.end(opposite);
          }
          if (inside != kind) {
            handler.start(kind);
          }
          yield kind;
        }
      };

      handler.line(kind, kind.clean(line));
    }

    if (inside != LineKind.BLANK) {
      handler.end(inside);
    }
    if (insideSection) {
      handler.end(LineKind.SECTION);
    }
    handler.endDocument();
  }

  /// Parses the `.jsh` file at `path` into a [Document].
  ///
  /// The file is read line by line.
  /// Lines that form the prologue (text lines followed by blank lines
  /// at the very top of the file) are dropped before parsing begins.
  ///
  /// The lines are parsed according to the rules described in
  /// the [DocumentParser] documentation.
  ///
  /// @param path the path to the `.jsh` file
  /// @return the parsed [Document]
  /// @throws NullPointerException if `path` is `null`
  /// @throws IOException if the file cannot be read
  public static Document parse(Path path) throws IOException {
    Objects.requireNonNull(path);
    List<String> lines;
    try(var stream = Files.lines(path)) {
      lines = stream.dropWhile(LineKind.is(LineKind.TEXT))
          .dropWhile(LineKind.is(LineKind.BLANK))
          .toList();
    }
    return parseLines(lines);
  }

  /// Parses a list of source lines into a [Document].
  ///
  /// This overload is called internally after prologue stripping.
  /// It sets up a stateful [EventHandler] that accumulates [Section]s
  /// and [Content] blocks as events arrive from [#transformTo].
  ///
  /// @param lines the source lines to parse, with the prologue already removed
  /// @return the parsed [Document]
  private static Document parseLines(List<String> lines) {
    var handler = new EventHandler() {
      private StringBuilder builder;
      private ArrayList<Content> contents;
      private String sectionTitle;
      private ArrayList<Section> sections;
      private Document document;

      @Override
      public void startDocument() {
        sections = new ArrayList<>();
      }
      @Override
      public void endDocument() {
        document = new Document(sections);
        sections = null;
      }

      @Override
      public void startSection() {
        contents = new ArrayList<>();
        sectionTitle = "";
      }
      @Override
      public void endSection() {
        sections.add(new Section(sectionTitle, contents));
        contents = null;
        sectionTitle = null;
      }

      @Override
      public void startText() {
        builder = new StringBuilder();
      }
      @Override
      public void endText() {
        contents.add(new Content(Content.Kind.TEXT, builder.toString()));
        builder = null;
      }

      @Override
      public void startCode() {
        builder = new StringBuilder();
      }

      @Override
      public void endCode() {
        contents.add(new Content(Content.Kind.CODE, builder.toString()));
        builder = null;
      }

      @Override
      public void line(LineKind kind, String line) {
        switch(kind) {
          case SECTION -> sectionTitle = line;
          case BLANK -> {}
          case CODE, TEXT -> {
            if (!builder.isEmpty()) {
              builder.append('\n');
            }
            builder.append(line);
          }
        }
      }
    };
    transformTo(lines, handler);
    return handler.document;
  }
}