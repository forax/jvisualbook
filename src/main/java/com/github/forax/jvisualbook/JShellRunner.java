package com.github.forax.jvisualbook;

import jdk.jshell.EvalException;
import jdk.jshell.JShell;
import jdk.jshell.JShellException;
import jdk.jshell.Snippet;
import jdk.jshell.SnippetEvent;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.io.UncheckedIOException;
import java.lang.reflect.UndeclaredThrowableException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.StringJoiner;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public final class JShellRunner {
  private JShellRunner() {
    throw new AssertionError();
  }

  public static Model.Execution evaluate(Model.Code code, int timeoutSeconds) {
    var output = new ByteArrayOutputStream();
    try (var out = new PrintStream(output, true, StandardCharsets.UTF_8);
         var err = new PrintStream(output, true, StandardCharsets.UTF_8);
         var executor = Executors.newVirtualThreadPerTaskExecutor();
         var shell = JShell.builder()
             .out(out)
             .err(err)
             .compilerOptions("--enable-preview", "--source=" + Runtime.version().feature())
             .remoteVMOptions("--enable-preview")
             .build()) {
      var future = executor.submit(() -> evaluateInShell(shell, output, code));
      try {
        return future.get(timeoutSeconds, TimeUnit.SECONDS);
      } catch (TimeoutException e) {
        shell.stop();
        var timeoutEval = new Model.Evaluation(
            Model.Evaluation.Status.ERROR,
            "Error: execution timed out after " + timeoutSeconds + " seconds");
        return new Model.Execution(Collections.nCopies(code.snippets().size(), timeoutEval));
      } catch (InterruptedException e) {
        shell.stop();
        throw new UncheckedIOException("Evaluation interrupted", new IOException(e));
      } catch (ExecutionException e) {
        switch (e.getCause()) {
          case RuntimeException runtimeException -> throw runtimeException;
          case Error error -> throw error;
          case Throwable throwable -> throw new UndeclaredThrowableException(throwable);
        }
      }
    }
  }

  private static Model.Execution evaluateInShell(JShell shell, ByteArrayOutputStream output, Model.Code code) {
    var evaluations = new ArrayList<Model.Evaluation>();
    for (var snippet : code.snippets()) {
      var evaluation = evaluateSnippet(shell, output, snippet);
      evaluations.add(evaluation);
      output.reset();
    }
    return new Model.Execution(evaluations);
  }

  private static Model.Evaluation evaluateSnippet(JShell shell, ByteArrayOutputStream output, Model.Snippet snippet) {
    var source = snippet.code();
    var analysis = shell.sourceCodeAnalysis();

    while (!source.isEmpty()) {
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
        return new Model.Evaluation(Model.Evaluation.Status.ERROR, e.getClass().getName() + ": " + e.getMessage());
      }

      var joiner = new StringJoiner("\n");
      for (var event : events) {
        switch (event.exception()) {
          case null -> {}
          case EvalException evalException -> {
            return new Model.Evaluation(Model.Evaluation.Status.ERROR, evalException.getExceptionClassName() + ": " + evalException.getMessage());
          }
          case JShellException shellException -> {
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
        return new Model.Evaluation(Model.Evaluation.Status.ERROR, rejected);
      }

      source = remaining;
    }

    var text = output.toString(StandardCharsets.UTF_8);
    return new Model.Evaluation(Model.Evaluation.Status.SUCCESS, text);
  }
}