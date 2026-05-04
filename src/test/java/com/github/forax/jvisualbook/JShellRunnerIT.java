package com.github.forax.jvisualbook;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public final class JShellRunnerIT {

  // --- evaluate(Code) ---

  @Test
  public void evaluateEmptyCode() {
    var code = new Model.Code(List.of());
    var execution = JShellRunner.evaluate(code);
    assertEquals(new Model.Execution(List.of()), execution);
  }

  @Test
  public void evaluateSingleSuccessfulSnippet() {
    var code = new Model.Code(List.of(new Model.Snippet("1 + 1;")));
    var execution = JShellRunner.evaluate(code);
    assertEquals(1, execution.evaluations().size());
    assertEquals(Model.Evaluation.Status.SUCCESS, execution.evaluations().getFirst().status());
  }

  @Test
  public void evaluatePrintlnCapturesOutput() {
    var code = new Model.Code(List.of(new Model.Snippet("IO.println(\"hello\");")));
    var execution = JShellRunner.evaluate(code);
    assertEquals(1, execution.evaluations().size());
    var eval = execution.evaluations().getFirst();
    assertEquals(Model.Evaluation.Status.SUCCESS, eval.status());
    assertEquals("hello\n", eval.text());
  }

  @Test
  public void evaluateStderrCapturedToo() {
    var code = new Model.Code(List.of(new Model.Snippet("System.err.println(\"oops\");")));
    var execution = JShellRunner.evaluate(code);
    var eval = execution.evaluations().getFirst();
    assertEquals(Model.Evaluation.Status.SUCCESS, eval.status());
    assertEquals("oops\n", eval.text());
  }

  @Test
  public void evaluateSyntaxErrorReturnsError() {
    var code = new Model.Code(List.of(new Model.Snippet("int x = ;")));
    var execution = JShellRunner.evaluate(code);
    assertEquals(1, execution.evaluations().size());
    var eval = execution.evaluations().getFirst();
    assertEquals(Model.Evaluation.Status.ERROR, eval.status());
    assertFalse(eval.text().isEmpty());
  }

  @Test
  public void evaluateUndefinedVariableReturnsError() {
    var code = new Model.Code(List.of(new Model.Snippet("int x = undefinedVar;")));
    var execution = JShellRunner.evaluate(code);
    var eval = execution.evaluations().getFirst();
    assertEquals(Model.Evaluation.Status.ERROR, eval.status());
  }

  @Test
  public void evaluateMultipleSnippetsIndependentResults() {
    var snippet1 = new Model.Snippet("IO.println(\"first\");");
    var snippet2 = new Model.Snippet("IO.println(\"second\");");
    var code = new Model.Code(List.of(snippet1, snippet2));
    var execution = JShellRunner.evaluate(code);
    assertEquals(2, execution.evaluations().size());
    assertEquals("first\n", execution.evaluations().get(0).text());
    assertEquals("second\n", execution.evaluations().get(1).text());
  }

  @Test
  public void evaluateOutputIsResetBetweenSnippets() {
    var snippet1 = new Model.Snippet("IO.println(\"a\");");
    var snippet2 = new Model.Snippet("IO.println(\"b\");");
    var code = new Model.Code(List.of(snippet1, snippet2));
    var execution = JShellRunner.evaluate(code);
    // Each snippet should only see its own output, not accumulated output
    assertFalse(execution.evaluations().get(1).text().contains("a"));
  }

  @Test
  public void evaluateSnippetWithMultipleStatements() {
    var snippet = new Model.Snippet("System.out.print(\"x\"); System.out.print(\"y\");");
    var code = new Model.Code(List.of(snippet));
    var execution = JShellRunner.evaluate(code);
    var eval = execution.evaluations().getFirst();
    assertEquals(Model.Evaluation.Status.SUCCESS, eval.status());
    assertEquals("xy", eval.text());
  }

  @Test
  public void evaluateVariableDeclarationSucceeds() {
    var code = new Model.Code(List.of(new Model.Snippet("int x = 42;")));
    var execution = JShellRunner.evaluate(code);
    assertEquals(Model.Evaluation.Status.SUCCESS, execution.evaluations().getFirst().status());
  }

  @Test
  public void evaluateVariableSharedAcrossSnippets() {
    var snippet1 = new Model.Snippet("int x = 42;");
    var snippet2 = new Model.Snippet("IO.println(x);");
    var code = new Model.Code(List.of(snippet1, snippet2));
    var execution = JShellRunner.evaluate(code);
    assertEquals(2, execution.evaluations().size());
    assertEquals(Model.Evaluation.Status.SUCCESS, execution.evaluations().get(0).status());
    assertEquals(Model.Evaluation.Status.SUCCESS, execution.evaluations().get(1).status());
    assertEquals("42\n", execution.evaluations().get(1).text());
  }

  @Test
  public void evaluateErrorDoesNotAffectSubsequentSnippets() {
    var snippet1 = new Model.Snippet("int x = !!;");
    var snippet2 = new Model.Snippet("IO.println(\"ok\");");
    var code = new Model.Code(List.of(snippet1, snippet2));
    var execution = JShellRunner.evaluate(code);
    assertEquals(2, execution.evaluations().size());
    assertEquals(Model.Evaluation.Status.ERROR, execution.evaluations().get(0).status());
    assertEquals(Model.Evaluation.Status.SUCCESS, execution.evaluations().get(1).status());
    assertEquals("ok\n", execution.evaluations().get(1).text());
  }

  @Test
  public void evaluateSuccessfulSnippetHasEmptyTextWhenNoOutput() {
    var code = new Model.Code(List.of(new Model.Snippet("int y = 5;")));
    var execution = JShellRunner.evaluate(code);
    var eval = execution.evaluations().getFirst();
    assertEquals(Model.Evaluation.Status.SUCCESS, eval.status());
    assertEquals("", eval.text());
  }

  @Test
  public void evaluateMethodDefinitionSucceeds() {
    var snippet = new Model.Snippet("int square(int n) { return n * n; }");
    var code = new Model.Code(List.of(snippet));
    var execution = JShellRunner.evaluate(code);
    assertEquals(Model.Evaluation.Status.SUCCESS, execution.evaluations().getFirst().status());
  }

  @Test
  public void evaluateSeveralLinesSucceeds() {
    var snippet = new Model.Snippet("""
        var a = 3;
        IO.println(a);
        """);
    var code = new Model.Code(List.of(snippet));
    var execution = JShellRunner.evaluate(code);
    assertEquals(Model.Evaluation.Status.SUCCESS, execution.evaluations().getFirst().status());
    assertEquals("3\n", execution.evaluations().getFirst().text());
  }

  // --- Parse/syntax errors ---

  @Test
  public void evaluateMissingClosingParenthesis() {
    var code = new Model.Code(List.of(new Model.Snippet("IO.println(\"hello\";")));
    var execution = JShellRunner.evaluate(code);
    assertEquals(Model.Evaluation.Status.ERROR, execution.evaluations().getFirst().status());
  }

  @Test
  public void evaluateMissingClosingBrace() {
    var code = new Model.Code(List.of(new Model.Snippet("if (true) { int x = 1;")));
    var execution = JShellRunner.evaluate(code);
    assertEquals(Model.Evaluation.Status.ERROR, execution.evaluations().getFirst().status());
  }

  @Test
  public void evaluateKeywordAsVariableName() {
    var code = new Model.Code(List.of(new Model.Snippet("int class = 5;")));
    var execution = JShellRunner.evaluate(code);
    assertEquals(Model.Evaluation.Status.ERROR, execution.evaluations().getFirst().status());
  }

  @Test
  public void evaluateKeywordAsMethodName() {
    var code = new Model.Code(List.of(new Model.Snippet("void return() {}")));
    var execution = JShellRunner.evaluate(code);
    assertEquals(Model.Evaluation.Status.ERROR, execution.evaluations().getFirst().status());
  }

  @Test
  public void evaluateKeywordAsParameterName() {
    var code = new Model.Code(List.of(new Model.Snippet("void foo(int while) {}")));
    var execution = JShellRunner.evaluate(code);
    assertEquals(Model.Evaluation.Status.ERROR, execution.evaluations().getFirst().status());
  }

  @Test
  public void evaluateDoubleSemicolon() {
    var code = new Model.Code(List.of(new Model.Snippet("int x = 1;;")));
    var execution = JShellRunner.evaluate(code);
    assertEquals(Model.Evaluation.Status.SUCCESS, execution.evaluations().getFirst().status());
  }

  @Test
  public void evaluateIncompleteMethodCall() {
    var code = new Model.Code(List.of(new Model.Snippet("Math.abs(")));
    var execution = JShellRunner.evaluate(code);
    assertEquals(Model.Evaluation.Status.ERROR, execution.evaluations().getFirst().status());
  }

  @Test
  public void evaluateMalformedTernary() {
    var code = new Model.Code(List.of(new Model.Snippet("int x = true ? 1;")));
    var execution = JShellRunner.evaluate(code);
    assertEquals(Model.Evaluation.Status.ERROR, execution.evaluations().getFirst().status());
  }

  @Test
  public void evaluateInvalidOperatorSequence() {
    var code = new Model.Code(List.of(new Model.Snippet("int x = 1 ++ + ++ 2;")));
    var execution = JShellRunner.evaluate(code);
    assertEquals(Model.Evaluation.Status.ERROR, execution.evaluations().getFirst().status());
  }

  @Test
  public void evaluateMissingTypeInDeclaration() {
    var code = new Model.Code(List.of(new Model.Snippet("x = 42;")));
    var execution = JShellRunner.evaluate(code);
    assertEquals(Model.Evaluation.Status.ERROR, execution.evaluations().getFirst().status());
  }

  @Test
  public void evaluateUnclosedStringLiteral() {
    var code = new Model.Code(List.of(new Model.Snippet("String s = \"hello;")));
    var execution = JShellRunner.evaluate(code);
    assertEquals(Model.Evaluation.Status.ERROR, execution.evaluations().getFirst().status());
  }

  @Test
  public void evaluateInvalidAnnotationSyntax() {
    var code = new Model.Code(List.of(new Model.Snippet("@123 int x = 1;")));
    var execution = JShellRunner.evaluate(code);
    assertEquals(Model.Evaluation.Status.ERROR, execution.evaluations().getFirst().status());
  }

  @Test
  public void evaluateMethodDefinitionAndCallAcrossSnippets() {
    var snippet1 = new Model.Snippet("int square(int n) { return n * n; }");
    var snippet2 = new Model.Snippet("IO.println(square(4));");
    var code = new Model.Code(List.of(snippet1, snippet2));
    var execution = JShellRunner.evaluate(code);
    assertEquals(Model.Evaluation.Status.SUCCESS, execution.evaluations().get(1).status());
    assertEquals("16\n", execution.evaluations().get(1).text());
  }
}