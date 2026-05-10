package com.github.forax.jvisualbook;

import jdk.jshell.EvalException;
import jdk.jshell.JShell;
import jdk.jshell.JShellException;
import jdk.jshell.Snippet;
import jdk.jshell.SnippetEvent;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.StringJoiner;
import java.util.stream.Collectors;

public final class JShellRunner {
  private JShellRunner() {
    throw new AssertionError();
  }

  public static Model.Execution evaluate(Model.Code code) {
    var output = new ByteArrayOutputStream();
    try (var shell = JShell.builder()
        .out(new PrintStream(output, true, StandardCharsets.UTF_8))
        .err(new PrintStream(output, true, StandardCharsets.UTF_8))
        .compilerOptions("--enable-preview", "--source=" + Runtime.version().feature())
        .remoteVMOptions("--enable-preview")
        .build()) {

      var evaluations = new ArrayList<Model.Evaluation>();
      for (var snippet : code.snippets()) {
        var evaluation = evaluateSnippet(shell, output, snippet);
        evaluations.add(evaluation);
      }

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
      var remaining = info.remaining();
      if (unit == null) {
        unit = remaining;
        remaining = "";
      }

      List<SnippetEvent> events;
      try {
        events = shell.eval(unit);
      } catch (RuntimeException e) {
        output.reset();
        return new Model.Evaluation(Model.Evaluation.Status.ERROR, e.getClass().getName() + ": " + e.getMessage());
      }

      var joiner = new StringJoiner("\n");
      for (var event : events) {
        //System.err.println("DEBUG " + unit + " " + event);
        switch (event.exception()) {
          case null -> {}
          case EvalException evalException -> {
            output.reset();
            return new Model.Evaluation(Model.Evaluation.Status.ERROR, evalException.getExceptionClassName() + ": " + evalException.getMessage());
          }
          case JShellException shellException ->  {
            output.reset();
            return new Model.Evaluation(Model.Evaluation.Status.ERROR, shellException.getClass().getName() + ": " + shellException.getMessage());
          }
        }
        if (event.status() == Snippet.Status.REJECTED) {
          var message = "Error: " + shell.diagnostics(event.snippet())
              .map(d -> d.getMessage(null))
              .findFirst()
              .orElseGet(() -> "error " + event);
          joiner.add(message);
        }
      }
      var rejected = joiner.toString();

      if (!rejected.isEmpty()) {
        output.reset();
        return new Model.Evaluation(Model.Evaluation.Status.ERROR, rejected);
      }

      source = remaining;
    }

    var text = output.toString(StandardCharsets.UTF_8);
    output.reset();
    return new Model.Evaluation(Model.Evaluation.Status.SUCCESS, text);
  }
}