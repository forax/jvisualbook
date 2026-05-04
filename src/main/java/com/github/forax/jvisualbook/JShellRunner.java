package com.github.forax.jvisualbook;

import jdk.jshell.JShell;
import java.util.stream.Collectors;

public final class JShellRunner {
  private JShellRunner() {
    throw new AssertionError();
  }

  public static Model.Execution evaluate(Model.Code code) {
    try (var shell = JShell.create()) {
      var evaluations = code.snippets().stream()
          .map(snippet -> evaluateSnippet(shell, snippet))
          .toList();
      return new Model.Execution(evaluations);
    }
  }

  private static Model.Evaluation evaluateSnippet(JShell shell, Model.Snippet snippet) {
    var events = shell.eval(snippet.code());
    var text = events.stream()
        .map(event -> {
          return switch (event.status()) {
            case VALID -> event.value() != null && !event.value().isEmpty()
                ? event.value()
                : "(ok)";
            case REJECTED -> "Error: " + shell.diagnostics(event.snippet())
                .map(d -> d.getMessage(null))
                .findFirst()
                .orElse("rejected");
            default -> "(" + event.status().name().toLowerCase() + ")";
          };
        })
        .collect(Collectors.joining("\n"));

    return new Model.Evaluation(text);
  }
}