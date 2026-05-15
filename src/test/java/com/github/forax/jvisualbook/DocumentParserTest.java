package com.github.forax.jvisualbook;

import com.github.forax.jvisualbook.Model.Content;
import com.github.forax.jvisualbook.Model.Document;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class DocumentParserTest {

  private static Document parseLines(String source) throws IOException {
    var tmp = Files.createTempFile("test", ".java");
    try {
      Files.writeString(tmp, source);
      return DocumentParser.parse(tmp);
    } finally {
      Files.deleteIfExists(tmp);
    }
  }


  @Test
  public void parsePathNullThrows(@TempDir Path dir) {
    assertThrows(NullPointerException.class, () -> DocumentParser.parse(null));
  }

  @Test
  public void parsePathEmptyFile(@TempDir Path dir) throws IOException {
    var file = dir.resolve("empty.java");
    Files.writeString(file, "");
    var doc = DocumentParser.parse(file);

    assertEquals(List.of(), doc.sections());
  }

  @Test
  public void parsePathStripsLeadingTextHeader(@TempDir Path dir) throws IOException {
    var file = dir.resolve("header.java");
    Files.writeString(file, """
        // This is a header comment
        // Another header line
        
        // # Section One
        // Some text
        int x = 1;
        """);
    var doc = DocumentParser.parse(file);

    assertEquals(1, doc.sections().size());
    assertEquals("# Section One", doc.sections().getFirst().title());
  }

  @Test
  public void parseSingleSection() throws IOException {
    var doc = parseLines("""
        // # My Section
        // Hello world
        """);

    assertEquals(1, doc.sections().size());
    assertEquals("# My Section", doc.sections().getFirst().title());
  }

  @Test
  public void parseMultipleSections() throws IOException {
    var doc = parseLines("""
        // # First
        // text one
        
        // ## Second
        // text two
        """);

    assertEquals(2, doc.sections().size());
    assertEquals("# First", doc.sections().get(0).title());
    assertEquals("## Second", doc.sections().get(1).title());
  }

  @Test
  public void parseCodeWithoutSectionCreatesSyntheticSection() throws IOException {
    var doc = parseLines("""
        int x = 1;
        """);

    assertEquals(1, doc.sections().size());
    var section = doc.sections().getFirst();
    assertEquals("", section.title());
    assertEquals(1, section.contents().size());
    assertEquals(Content.Kind.CODE, section.contents().getFirst().kind());
  }

  @Test
  public void parseTextWithoutSectionCreatesSyntheticSection() throws IOException {
    var doc = parseLines("""
        
        // Just some text
        """);

    assertEquals(1, doc.sections().size());
    var section = doc.sections().getFirst();
    assertEquals("", section.title());
    assertEquals(Content.Kind.TEXT, section.contents().getFirst().kind());
  }

  @Test
  public void parseSectionWithTextContent() throws IOException {
    var doc = parseLines("""
        // # Section
        // line one
        // line two
        """);

    var contents = doc.sections().getFirst().contents();
    assertEquals(1, contents.size());
    assertEquals(Content.Kind.TEXT, contents.getFirst().kind());
    assertEquals("line one\nline two", contents.getFirst().text());
  }

  @Test
  public void parseSectionWithTextStripsPrefix() throws IOException {
    var doc = parseLines("""
        // # Section
        // hello
        """);

    var text = doc.sections().getFirst().contents().getFirst().text();
    assertEquals("hello", text);
  }

  @Test
  public void parseSectionWithCodeContent() throws IOException {
    var doc = parseLines("""
        // # Section
        int x = 1;
        int y = 2;
        """);

    var contents = doc.sections().getFirst().contents();
    assertEquals(1, contents.size());
    assertEquals(Content.Kind.CODE, contents.getFirst().kind());
    assertEquals("int x = 1;\nint y = 2;", contents.getFirst().text());
  }

  @Test
  public void parseSectionWithTextThenCode() throws IOException {
    var doc = parseLines("""
        // # Section
        // some description
        int x = 42;
        """);

    var contents = doc.sections().getFirst().contents();
    assertEquals(2, contents.size());
    assertEquals(Content.Kind.TEXT, contents.get(0).kind());
    assertEquals("some description", contents.get(0).text());
    assertEquals(Content.Kind.CODE, contents.get(1).kind());
    assertEquals("int x = 42;", contents.get(1).text());
  }

  @Test
  public void parseSectionWithCodeThenText() throws IOException {
    var doc = parseLines("""
        // # Section
        int x = 1;
        
        // more explanation
        """);

    var contents = doc.sections().getFirst().contents();
    assertEquals(2, contents.size());
    assertEquals(Content.Kind.CODE, contents.get(0).kind());
    assertEquals(Content.Kind.TEXT, contents.get(1).kind());
  }

  @Test
  public void parseSectionWithInterleavedTextAndCode() throws IOException {
    var doc = parseLines("""
        // # Section
        // intro
        int a = 1;
        
        // outro
        int b = 2;
        """);

    var contents = doc.sections().getFirst().contents();
    assertEquals(4, contents.size());
    assertEquals(Content.Kind.TEXT, contents.get(0).kind());
    assertEquals(Content.Kind.CODE, contents.get(1).kind());
    assertEquals(Content.Kind.TEXT, contents.get(2).kind());
    assertEquals(Content.Kind.CODE, contents.get(3).kind());
  }

  @Test
  public void parseSectionDirectlyAfterCode() throws IOException {
    // Covers: SECTION case -> handler.end(inside) when inside == CODE
    var doc = parseLines("""
        // # First
        int x = 1;
        // # Second
        // text
        """);

    assertEquals(2, doc.sections().size());
    var first = doc.sections().get(0);
    assertEquals(1, first.contents().size());
    assertEquals(Content.Kind.CODE, first.contents().getFirst().kind());
    assertEquals("int x = 1;", first.contents().getFirst().text());
  }

  @Test
  public void parseTextDirectlyAfterCode() throws IOException {
    // Covers: TEXT case -> handler.end(CODE) when inside == CODE
    var doc = parseLines("""
        // # Section
        int x = 1;
        // some text
        """);

    var contents = doc.sections().getFirst().contents();
    assertEquals(2, contents.size());
    assertEquals(Content.Kind.CODE, contents.get(0).kind());
    assertEquals("int x = 1;", contents.get(0).text());
    assertEquals(Content.Kind.TEXT, contents.get(1).kind());
    assertEquals("some text", contents.get(1).text());
  }

  @Test
  public void parseBlankLinesBetweenSectionsAreIgnored() throws IOException {
    var doc = parseLines("""
        // # First
        // text
        
        
        // # Second
        // more text
        """);
    assertEquals(2, doc.sections().size());
  }

  @Test
  public void parseTrailingBlankLinesIgnored() throws IOException {
    var doc = parseLines("""
        // # Section
        // text
        
        
        """);
    assertEquals(1, doc.sections().size());
    assertEquals(1, doc.sections().getFirst().contents().size());
  }

  @Test
  public void parseEmptySectionHasNoContents() throws IOException {
    var doc = parseLines("""
        // # Empty Section
        
        // # Next Section
        // some text
        """);

    assertEquals(2, doc.sections().size());
    assertEquals(List.of(), doc.sections().getFirst().contents());
  }

  @Test
  public void parseCodeBlockMultiLine() throws IOException {
    var doc = parseLines("""
        // # Section
        void foo() {
          return;
        }
        """);

    var code = doc.sections().getFirst().contents().getFirst().text();
    assertEquals("void foo() {\n  return;\n}", code);
  }
}