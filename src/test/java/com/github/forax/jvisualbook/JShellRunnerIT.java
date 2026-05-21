package com.github.forax.jvisualbook;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

public final class JShellRunnerIT {

  private static final int DEFAULT_TIMEOUT_MILLIS = 3000;

  // --- evaluate(Code) ---

  @Test
  public void evaluateEmptyCode() throws InterruptedException {
    var program = new Model.Program(List.of());
    var execution = JShellRunner.evaluate(program, DEFAULT_TIMEOUT_MILLIS);
    assertEquals(new Model.Execution(List.of()), execution);
  }

  @Test
  public void evaluateSingleSuccessfulSnippet() throws InterruptedException {
    var program = new Model.Program(List.of(new Model.Snippet("1 + 1;")));
    var execution = JShellRunner.evaluate(program, DEFAULT_TIMEOUT_MILLIS);
    assertEquals(1, execution.evaluations().size());
    assertEquals(Model.Evaluation.Status.SUCCESS, execution.evaluations().getFirst().status());
  }

  @Test
  public void evaluatePrintlnCapturesOutput() throws InterruptedException {
    var program = new Model.Program(List.of(new Model.Snippet("IO.println(\"hello\");")));
    var execution = JShellRunner.evaluate(program, DEFAULT_TIMEOUT_MILLIS);
    assertEquals(1, execution.evaluations().size());
    var eval = execution.evaluations().getFirst();
    assertEquals(Model.Evaluation.Status.SUCCESS, eval.status());
    assertEquals("hello\n", eval.text());
  }

  @Test
  public void evaluateStderrCapturedToo() throws InterruptedException {
    var program = new Model.Program(List.of(new Model.Snippet("System.err.println(\"oops\");")));
    var execution = JShellRunner.evaluate(program, DEFAULT_TIMEOUT_MILLIS);
    var eval = execution.evaluations().getFirst();
    assertEquals(Model.Evaluation.Status.SUCCESS, eval.status());
    assertEquals("oops\n", eval.text());
  }

  @Test
  public void evaluateSyntaxErrorReturnsError() throws InterruptedException {
    var program = new Model.Program(List.of(new Model.Snippet("int x = ;")));
    var execution = JShellRunner.evaluate(program, DEFAULT_TIMEOUT_MILLIS);
    assertEquals(1, execution.evaluations().size());
    var eval = execution.evaluations().getFirst();
    assertEquals(Model.Evaluation.Status.ERROR, eval.status());
    assertFalse(eval.text().isEmpty());
  }

  @Test
  public void evaluateUndefinedVariableReturnsError() throws InterruptedException {
    var program = new Model.Program(List.of(new Model.Snippet("int x = undefinedVar;")));
    var execution = JShellRunner.evaluate(program, DEFAULT_TIMEOUT_MILLIS);
    var eval = execution.evaluations().getFirst();
    assertEquals(Model.Evaluation.Status.ERROR, eval.status());
  }

  @Test
  public void evaluateMultipleSnippetsIndependentResults() throws InterruptedException {
    var snippet1 = new Model.Snippet("IO.println(\"first\");");
    var snippet2 = new Model.Snippet("IO.println(\"second\");");
    var program = new Model.Program(List.of(snippet1, snippet2));
    var execution = JShellRunner.evaluate(program, DEFAULT_TIMEOUT_MILLIS);
    assertEquals(2, execution.evaluations().size());
    assertEquals("first\n", execution.evaluations().get(0).text());
    assertEquals("second\n", execution.evaluations().get(1).text());
  }

  @Test
  public void evaluateOutputIsResetBetweenSnippets() throws InterruptedException {
    var snippet1 = new Model.Snippet("IO.println(\"a\");");
    var snippet2 = new Model.Snippet("IO.println(\"b\");");
    var program = new Model.Program(List.of(snippet1, snippet2));
    var execution = JShellRunner.evaluate(program, DEFAULT_TIMEOUT_MILLIS);
    // Each snippet should only see its own output, not accumulated output
    assertFalse(execution.evaluations().get(1).text().contains("a"));
  }

  @Test
  public void evaluateSnippetWithMultipleStatements() throws InterruptedException {
    var snippet = new Model.Snippet("System.out.print(\"x\"); System.out.print(\"y\");");
    var program = new Model.Program(List.of(snippet));
    var execution = JShellRunner.evaluate(program, DEFAULT_TIMEOUT_MILLIS);
    var eval = execution.evaluations().getFirst();
    assertEquals(Model.Evaluation.Status.SUCCESS, eval.status());
    assertEquals("xy", eval.text());
  }

  @Test
  public void evaluateVariableDeclarationSucceeds() throws InterruptedException {
    var program = new Model.Program(List.of(new Model.Snippet("int x = 42;")));
    var execution = JShellRunner.evaluate(program, DEFAULT_TIMEOUT_MILLIS);
    assertEquals(Model.Evaluation.Status.SUCCESS, execution.evaluations().getFirst().status());
  }

  @Test
  public void evaluateVariableSharedAcrossSnippets() throws InterruptedException {
    var snippet1 = new Model.Snippet("int x = 42;");
    var snippet2 = new Model.Snippet("IO.println(x);");
    var program = new Model.Program(List.of(snippet1, snippet2));
    var execution = JShellRunner.evaluate(program, DEFAULT_TIMEOUT_MILLIS);
    assertEquals(2, execution.evaluations().size());
    assertEquals(Model.Evaluation.Status.SUCCESS, execution.evaluations().get(0).status());
    assertEquals(Model.Evaluation.Status.SUCCESS, execution.evaluations().get(1).status());
    assertEquals("42\n", execution.evaluations().get(1).text());
  }

  @Test
  public void evaluateErrorDoesNotAffectSubsequentSnippets() throws InterruptedException {
    var snippet1 = new Model.Snippet("int x = !!;");
    var snippet2 = new Model.Snippet("IO.println(\"ok\");");
    var program = new Model.Program(List.of(snippet1, snippet2));
    var execution = JShellRunner.evaluate(program, DEFAULT_TIMEOUT_MILLIS);
    assertEquals(2, execution.evaluations().size());
    assertEquals(Model.Evaluation.Status.ERROR, execution.evaluations().get(0).status());
    assertEquals(Model.Evaluation.Status.SUCCESS, execution.evaluations().get(1).status());
    assertEquals("ok\n", execution.evaluations().get(1).text());
  }

  @Test
  public void evaluateSuccessfulSnippetHasEmptyTextWhenNoOutput() throws InterruptedException {
    var program = new Model.Program(List.of(new Model.Snippet("int y = 5;")));
    var execution = JShellRunner.evaluate(program, DEFAULT_TIMEOUT_MILLIS);
    var eval = execution.evaluations().getFirst();
    assertEquals(Model.Evaluation.Status.SUCCESS, eval.status());
    assertEquals("", eval.text());
  }

  @Test
  public void evaluateMethodDefinitionSucceeds() throws InterruptedException {
    var snippet = new Model.Snippet("int square(int n) { return n * n; }");
    var program = new Model.Program(List.of(snippet));
    var execution = JShellRunner.evaluate(program, DEFAULT_TIMEOUT_MILLIS);
    assertEquals(Model.Evaluation.Status.SUCCESS, execution.evaluations().getFirst().status());
  }

  @Test
  public void evaluateSeveralLinesSucceeds() throws InterruptedException {
    var snippet = new Model.Snippet("""
        var a = 3;
        IO.println(a);
        """);
    var program = new Model.Program(List.of(snippet));
    var execution = JShellRunner.evaluate(program, DEFAULT_TIMEOUT_MILLIS);
    assertEquals(Model.Evaluation.Status.SUCCESS, execution.evaluations().getFirst().status());
    assertEquals("3\n", execution.evaluations().getFirst().text());
  }

  private static boolean isValueClassEnabled() {
    try {
      Class.class.getMethod("isValue");
      return true;
    } catch (NoSuchMethodException e) {
      return false;
    }
  }

  @Test
  public void evaluateValueClassAcmp() throws InterruptedException {
    assumeTrue(isValueClassEnabled());

    var snippet = new Model.Snippet("""
        value record Point(int x, int y) {}
        
        Point p1 = new Point(1, 2);
        Point p2 = new Point(1, 2);
        IO.println(p1 == p2);  // true
        """);
    var program = new Model.Program(List.of(snippet));
    var execution = JShellRunner.evaluate(program, DEFAULT_TIMEOUT_MILLIS);
    var expected = new Model.Execution(
        List.of(new Model.Evaluation(Model.Evaluation.Status.SUCCESS, "true\n")));
    assertEquals(expected, execution);
  }

  @Test
  public void evaluateSynchronizedWithAValueClass() throws InterruptedException {
    assumeTrue(isValueClassEnabled());

    var snippet = new Model.Snippet("""
        value record Point(int x, int y) {}
        
        Point p = new Point(1, 2);
        Object o = p;
        synchronized(o) { }
        """);
    var program = new Model.Program(List.of(snippet));
    var execution = JShellRunner.evaluate(program, DEFAULT_TIMEOUT_MILLIS);
    assertEquals(1, execution.evaluations().size());
    assertEquals(Model.Evaluation.Status.ERROR, execution.evaluations().getFirst().status());
  }

  // --- Parse/syntax errors ---

  @Test
  public void evaluateMissingClosingParenthesis() throws InterruptedException {
    var program = new Model.Program(List.of(new Model.Snippet("IO.println(\"hello\";")));
    var execution = JShellRunner.evaluate(program, DEFAULT_TIMEOUT_MILLIS);
    assertEquals(Model.Evaluation.Status.ERROR, execution.evaluations().getFirst().status());
  }

  @Test
  public void evaluateMissingClosingBrace() throws InterruptedException {
    var program = new Model.Program(List.of(new Model.Snippet("if (true) { int x = 1;")));
    var execution = JShellRunner.evaluate(program, DEFAULT_TIMEOUT_MILLIS);
    assertEquals(Model.Evaluation.Status.ERROR, execution.evaluations().getFirst().status());
  }

  @Test
  public void evaluateKeywordAsVariableName() throws InterruptedException {
    var program = new Model.Program(List.of(new Model.Snippet("int class = 5;")));
    var execution = JShellRunner.evaluate(program, DEFAULT_TIMEOUT_MILLIS);
    assertEquals(Model.Evaluation.Status.ERROR, execution.evaluations().getFirst().status());
  }

  @Test
  public void evaluateKeywordAsMethodName() throws InterruptedException {
    var program = new Model.Program(List.of(new Model.Snippet("void return() {}")));
    var execution = JShellRunner.evaluate(program, DEFAULT_TIMEOUT_MILLIS);
    assertEquals(Model.Evaluation.Status.ERROR, execution.evaluations().getFirst().status());
  }

  @Test
  public void evaluateKeywordAsParameterName() throws InterruptedException {
    var program = new Model.Program(List.of(new Model.Snippet("void foo(int while) {}")));
    var execution = JShellRunner.evaluate(program, DEFAULT_TIMEOUT_MILLIS);
    assertEquals(Model.Evaluation.Status.ERROR, execution.evaluations().getFirst().status());
  }

  @Test
  public void evaluateDoubleSemicolon() throws InterruptedException {
    var program = new Model.Program(List.of(new Model.Snippet("int x = 1;;")));
    var execution = JShellRunner.evaluate(program, DEFAULT_TIMEOUT_MILLIS);
    assertEquals(Model.Evaluation.Status.SUCCESS, execution.evaluations().getFirst().status());
  }

  @Test
  public void evaluateIncompleteMethodCall() throws InterruptedException {
    var program = new Model.Program(List.of(new Model.Snippet("Math.abs(")));
    var execution = JShellRunner.evaluate(program, DEFAULT_TIMEOUT_MILLIS);
    assertEquals(Model.Evaluation.Status.ERROR, execution.evaluations().getFirst().status());
  }

  @Test
  public void evaluateMalformedTernary() throws InterruptedException {
    var program = new Model.Program(List.of(new Model.Snippet("int x = true ? 1;")));
    var execution = JShellRunner.evaluate(program, DEFAULT_TIMEOUT_MILLIS);
    assertEquals(Model.Evaluation.Status.ERROR, execution.evaluations().getFirst().status());
  }

  @Test
  public void evaluateInvalidOperatorSequence() throws InterruptedException {
    var program = new Model.Program(List.of(new Model.Snippet("int x = 1 ++ + ++ 2;")));
    var execution = JShellRunner.evaluate(program, DEFAULT_TIMEOUT_MILLIS);
    assertEquals(Model.Evaluation.Status.ERROR, execution.evaluations().getFirst().status());
  }

  @Test
  public void evaluateMissingTypeInDeclaration() throws InterruptedException {
    var program = new Model.Program(List.of(new Model.Snippet("x = 42;")));
    var execution = JShellRunner.evaluate(program, DEFAULT_TIMEOUT_MILLIS);
    assertEquals(Model.Evaluation.Status.ERROR, execution.evaluations().getFirst().status());
  }

  @Test
  public void evaluateUnclosedStringLiteral() throws InterruptedException {
    var program = new Model.Program(List.of(new Model.Snippet("String s = \"hello;")));
    var execution = JShellRunner.evaluate(program, DEFAULT_TIMEOUT_MILLIS);
    assertEquals(Model.Evaluation.Status.ERROR, execution.evaluations().getFirst().status());
  }

  @Test
  public void evaluateInvalidAnnotationSyntax() throws InterruptedException {
    var program = new Model.Program(List.of(new Model.Snippet("@123 int x = 1;")));
    var execution = JShellRunner.evaluate(program, DEFAULT_TIMEOUT_MILLIS);
    assertEquals(Model.Evaluation.Status.ERROR, execution.evaluations().getFirst().status());
  }

  @Test
  public void evaluateMethodDefinitionAndCallAcrossSnippets() throws InterruptedException {
    var snippet1 = new Model.Snippet("int square(int n) { return n * n; }");
    var snippet2 = new Model.Snippet("IO.println(square(4));");
    var program = new Model.Program(List.of(snippet1, snippet2));
    var execution = JShellRunner.evaluate(program, DEFAULT_TIMEOUT_MILLIS);
    assertEquals(Model.Evaluation.Status.SUCCESS, execution.evaluations().get(1).status());
    assertEquals("16\n", execution.evaluations().get(1).text());
  }

  @Test
  public void evaluateMissingRefrence() throws InterruptedException {
    var program = new Model.Program(List.of(new Model.Snippet("""
        IO.println(a);
        """)));
    var execution = JShellRunner.evaluate(program, DEFAULT_TIMEOUT_MILLIS);
    assertEquals(Model.Evaluation.Status.ERROR, execution.evaluations().getFirst().status());
  }

  // --- timeout ---

  @Test
  public void evaluateTimeoutReturnsErrorForAllSnippets() throws InterruptedException {
    var snippet1 = new Model.Snippet("for(;;);");
    var snippet2 = new Model.Snippet("""
        IO.println("never");
        """);
    var program = new Model.Program(List.of(snippet1, snippet2));
    var execution = JShellRunner.evaluate(program, 100);
    assertEquals(2, execution.evaluations().size());
    assertTrue(execution.evaluations().stream()
        .allMatch(e -> e.status() == Model.Evaluation.Status.ERROR));
  }

  @Test
  public void evaluateTimeoutMessageMentionsTimeout() throws InterruptedException {
    var program = new Model.Program(List.of(new Model.Snippet("while (true) {}")));
    var execution = JShellRunner.evaluate(program, 100);
    var eval = execution.evaluations().getFirst();
    assertEquals(Model.Evaluation.Status.ERROR, eval.status());
    assertTrue(eval.text().contains("timed out"));
  }

  @Test
  public void evaluateTimeoutDoesNotHangForever() {
    var program = new Model.Program(List.of(new Model.Snippet("while (true) {}")));
    assertTimeoutPreemptively(Duration.ofMillis(DEFAULT_TIMEOUT_MILLIS), () -> JShellRunner.evaluate(program, 100));
  }

  @Test
  public void evaluateSnippetAfterTimeoutIsIndependent() throws InterruptedException {
    var slow = new Model.Program(List.of(new Model.Snippet("for(;;);")));
    JShellRunner.evaluate(slow, 100);

    var fast = new Model.Program(List.of(new Model.Snippet("""
        IO.println("ok");
        """)));
    var execution = JShellRunner.evaluate(fast, DEFAULT_TIMEOUT_MILLIS);
    var eval = execution.evaluations().getFirst();
    assertEquals(Model.Evaluation.Status.SUCCESS, eval.status());
    assertEquals("ok\n", eval.text());
  }
}