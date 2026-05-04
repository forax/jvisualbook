package com.github.forax.jvisualbook;

import jdk.jshell.JShell;
import jdk.jshell.Snippet;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.stream.Collectors;

public final class JShellRunner {
  private JShellRunner() {
    throw new AssertionError();
  }

  public static Model.Execution evaluate(Model.Code code) {
    var output = new ByteArrayOutputStream();
    try (var shell = JShell.builder()
        .out(new PrintStream(output))
        .err(new PrintStream(output))
        .compilerOptions("--enable-preview", "--release=" + Runtime.version().feature())
        .build()) {
      var evaluations = code.snippets().stream()
          .map(snippet -> evaluateSnippet(shell, output, snippet))
          .toList();
      return new Model.Execution(evaluations);
    }
  }

  private static Model.Evaluation evaluateSnippet(JShell shell, ByteArrayOutputStream output, Model.Snippet snippet) {
    var source = snippet.code();
    var analysis = shell.sourceCodeAnalysis();

    while (!source.isEmpty()) {
      // Split off the next complete snippet from the source
      var info = analysis.analyzeCompletion(source);
      var unit = info.source();

      var events = shell.eval(unit);

      var rejected = events.stream()
          .filter(event -> event.status() == Snippet.Status.REJECTED)
          .map(event -> "Error: " + shell.diagnostics(event.snippet())
              .map(d -> d.getMessage(null))
              .findFirst()
              .orElseGet(() -> "error " + event))
          .collect(Collectors.joining("\n"));

      if (!rejected.isEmpty()) {
        return new Model.Evaluation(Model.Evaluation.Status.ERROR, rejected);
      }

      // Advance past the consumed snippet
      source = info.remaining();
    }

    var text = output.toString(StandardCharsets.UTF_8);
    output.reset();
    return new Model.Evaluation(Model.Evaluation.Status.SUCCESS, text);
  }
}