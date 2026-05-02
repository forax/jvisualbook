package com.github.forax.jvisualbook;

import com.github.forax.jvisualbook.model.Code;
import com.github.forax.jvisualbook.model.Content;
import com.github.forax.jvisualbook.model.Document;
import com.github.forax.jvisualbook.model.Section;
import com.github.forax.jvisualbook.model.Text;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;

public final class DocumentParser {
  private DocumentParser() {
    throw new AssertionError();
  }

  private enum LineKind {
    TEXT,
    SECTION,
    BLANK,
    CODE,
    ;

    String clean(String line) {
      return switch(this) {
        case BLANK, CODE -> line;
        case TEXT, SECTION -> line.substring(3);
      };
    }

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

    static Predicate<String> is(LineKind kind) {
      return line -> kind(line) == kind;
    }
  }

  private interface EventHandler {
    default void startDocument() { /*empty*/ }
    default void endDocument() { /*empty*/ }
    default void start(LineKind kind) {
      switch(kind) {
        case CODE -> startCode();
        case TEXT -> startText();
        case SECTION -> startSection();
        //$CASES-OMITTED$
        default -> throw new AssertionError();
      }
    }
    default void end(LineKind kind) {
      switch(kind) {
        case CODE -> endCode();
        case TEXT -> endText();
        case SECTION -> endSection();
        //$CASES-OMITTED$
        default -> throw new AssertionError();
      }
    }
    default void startCode() { /*empty*/ }
    default void endCode() { /*empty*/ }
    default void startText() { /*empty*/ }
    default void endText() { /*empty*/ }
    default void startSection() { /*empty*/ }
    default void endSection() { /*empty*/ }
    void line(LineKind kind, String line);
  }

  private static void transformTo(List<String> lines, EventHandler handler) {
    handler.startDocument();

    var inside = LineKind.BLANK;
    var insideSection = false;
    for(var line: lines) {
      var kind = LineKind.kind(line);

      //System.out.println(kind + " " + line);

      inside = switch(kind) {
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
          handler.start(LineKind.TEXT);
          yield LineKind.TEXT;
        }
        case TEXT -> {
          if (inside == LineKind.CODE) {
            handler.end(LineKind.CODE);
          }
          if (inside != LineKind.TEXT) {
            handler.start(LineKind.TEXT);
          }
          yield kind;
        }
        case CODE -> {
          if (inside == LineKind.TEXT) {
            handler.end(LineKind.TEXT);
          }
          if (inside != LineKind.CODE) {
            handler.start(LineKind.CODE);
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

  public static Document parse(Path path) throws IOException {
    Objects.requireNonNull(path);

    List<String> lines;
    try(var stream = Files.lines(path)) {
      // Remove the header (all texts before the blank lines)
      lines = stream.dropWhile(LineKind.is(LineKind.TEXT))
          .dropWhile(LineKind.is(LineKind.BLANK))
          .toList();
    }

    return parse(lines);
  }

  private static Document parse(List<String> lines) {
    var handler = new EventHandler() {
      private StringBuilder builder;
      private ArrayList<Content> contents;
      private String sectionTitle;
      private ArrayList<Section> sections;
      private Document document;

      /* DEBUG
      @Override
      public void start(LineKind kind) {
        System.err.println("DocumentParser::start " + kind);
        EventHandler.super.start(kind);
      }
      @Override
      public void end(LineKind kind) {
        System.err.println("DocumentParser::end " + kind);
        EventHandler.super.end(kind);
      }*/

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
        contents.add(new Text(builder.toString()));
        builder = null;
      }

      @Override
      public void startCode() {
        builder = new StringBuilder();
      }

      @Override
      public void endCode() {
        contents.add(new Code(builder.toString()));
        builder = null;
      }

      @Override
      public void line(LineKind kind, String line) {
        //System.err.println("DocumentParser::line " + kind + " " + line);
        switch(kind) {
          case SECTION -> {
            sectionTitle = line;
          }
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
