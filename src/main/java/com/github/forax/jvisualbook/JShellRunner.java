package com.github.forax.jvisualbook;

import jdk.jshell.EvalException;
import jdk.jshell.JShell;
import jdk.jshell.JShellException;
import jdk.jshell.Snippet;
import jdk.jshell.SnippetEvent;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.lang.reflect.UndeclaredThrowableException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.StringJoiner;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/// Evaluates [Model.Program] objects by running their [Model.Snippet]s
/// through a fresh JShell session.
///
/// Each call to [#evaluate] creates a new [JShell] instance, executes all
/// snippets sequentially in that session, and then closes it.
///
/// Declarations made in an earlier snippet (variables, methods, classes, ...)
/// are therefore visible to every later snippet in the same call,
/// but no state persists across calls.
///
/// ## Output capture
///
/// Each [Model.Evaluation] contains only the output (`stdout` and `stderr`)
/// produced by its own snippet.
///
/// ## Preview features
///
/// The JShell instance is configured with `--enable-preview`,
/// so snippets can use Java preview features that match the running JDK.
///
/// ## Timeout
///
/// Evaluation runs on a virtual-thread executor. If it does not complete within
/// `timeoutSeconds`, [JShell#stop()] is called to interrupt the remote JVM and
/// every snippet in the [Model.Program] is returned as a [Model.Evaluation.Status#ERROR]
/// with a "timed out" message.
///
/// ## Error handling
///
/// Three categories of failure are mapped to [Model.Evaluation.Status#ERROR]:
///
/// - **Compile-time rejection**: a snippet whose [Snippet.Status] is
///   `REJECTED`; the first compiler diagnostic is used as the error text.
/// - **Runtime exception**: an [EvalException] or other [JShellException]
///   thrown during evaluation; the exception class name and message are used.
/// - **Unexpected runner exception**: a [RuntimeException] thrown by
///   [JShell#eval(String)] itself (not by the evaluated code); propagated as-is.
///
/// @see Model.Program
/// @see Model.Execution
public final class JShellRunner {

  /// This class is a utility class
  private JShellRunner() {
    throw new AssertionError();
  }

  /// Evaluates all snippets in `program` and returns one [Model.Evaluation]
  /// per snippet, in submission order.
  ///
  /// A new [JShell] session is created for each call, so no state leaks between
  /// invocations. The session is always closed when this method returns,
  /// whether normally, via timeout, or via an unexpected exception.
  ///
  /// If execution exceeds `timeoutSeconds`, [JShell#stop()] is called and the
  /// returned [Model.Execution] contains one `ERROR` evaluation for every
  /// snippet in `code`.
  ///
  /// @param program the program to evaluate; must not be `null`
  /// @param timeoutMillis the maximum number of milliseconds to wait before
  ///                      forcibly stopping the JShell session; must be positive
  /// @return an [Model.Execution] whose `evaluations` list has the same size as
  ///         `code.snippets()`; never `null`
  /// @throws InterruptedException if the current thread is interrupted
  /// @throws UndeclaredThrowableException if [JShell#eval(String)] throws a checked exception.
  /// @throws NullPointerException if `program` is `null`
  /// @throws IllegalArgumentException if `timeoutMillis` is negative
  public static Model.Execution evaluate(Model.Program program, int timeoutMillis) throws InterruptedException{
    Objects.requireNonNull(program);
    if (timeoutMillis <= 0) {
      throw new IllegalArgumentException("timeoutMs <= 0");
    }
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
      var future = executor.submit(() -> evaluateInShell(shell, output, program));
      try {
        return future.get(timeoutMillis, TimeUnit.MILLISECONDS);
      } catch (TimeoutException e) {
        shell.stop();
        var timeoutEval = new Model.Evaluation(
            Model.Evaluation.Status.ERROR,
            "Error: execution timed out after " + timeoutMillis + " milliseconds");
        return new Model.Execution(Collections.nCopies(program.snippets().size(), timeoutEval));
      } catch (InterruptedException e) {
        shell.stop();
        throw e;
      } catch (ExecutionException e) {
        switch (e.getCause()) {
          case RuntimeException runtimeException -> throw runtimeException;
          case Error error -> throw error;
          case Throwable throwable -> throw new UndeclaredThrowableException(throwable);
        }
      }
    }
  }

  /// Iterates over the snippets in `code` and evaluates each one in sequence.
  ///
  /// @param shell  the live JShell session to evaluate snippets in
  /// @param output the shared buffer capturing `stdout` and `stderr`
  /// @param program the program whose snippets are to be evaluated
  /// @return an [Model.Execution] containing one evaluation per snippet
  private static Model.Execution evaluateInShell(JShell shell, ByteArrayOutputStream output, Model.Program program) {
    var evaluations = new ArrayList<Model.Evaluation>();
    for (var snippet : program.snippets()) {
      var evaluation = evaluateSnippet(shell, output, snippet);
      evaluations.add(evaluation);
      output.reset();
    }
    return new Model.Execution(evaluations);
  }

  /// Evaluates a single [Model.Snippet] and returns its [Model.Evaluation].
  ///
  /// Because JShell's [JShell#eval(String)] accepts only one complete compilation unit
  /// at a time, the snippet source is fed through
  /// [jdk.jshell.SourceCodeAnalysis#analyzeCompletion] in a loop to split it
  /// into individually complete units before submission.
  /// This allows a snippet to contain multiple statements or declarations.
  ///
  /// The method returns [Model.Evaluation.Status#ERROR] early on the first
  /// failure encountered:
  ///
  /// - A [Snippet.Status#REJECTED] event (compile error), using the first
  ///   compiler diagnostic as the error text;
  /// - An [EvalException] (runtime exception thrown by the evaluated code),
  ///   using `exceptionClassName: message` as the error text;
  /// - Any other [JShellException], formatted the same way;
  /// - A [RuntimeException] thrown by [JShell#eval(String)] itself (runner-level error).
  ///
  /// If no failure occurs, the text captured in `output` up to this point is
  /// returned as a [Model.Evaluation.Status#SUCCESS] evaluation.
  /// An empty string indicates that the snippet produced no output.
  ///
  /// @param shell   the live JShell session
  /// @param output  the shared buffer that has been capturing `stdout`/`stderr`
  ///                since the last reset; read but not reset by this method
  /// @param snippet the snippet to evaluate
  /// @return the evaluation result; never `null`
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