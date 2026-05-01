package com.github.forax.jvisualbook;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

public final class DocumentParserTest {
  @Test
  public void parse() throws IOException {
    var document = DocumentParser.parse(Path.of("intro.jsh"));
    IO.println(document);
  }
}