package com.github.forax.jvisualbook;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.lang.classfile.ClassFile;
import java.lang.constant.ClassDesc;
import java.lang.constant.ConstantDescs;
import java.lang.constant.MethodTypeDesc;
import java.lang.reflect.AccessFlag;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
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


  // --- environment ---

  private static void createSyntheticClass(Path dir) throws IOException {
    var packageFolder = dir.resolve("foo");
    Files.createDirectories(packageFolder);
    var path = packageFolder.resolve("Bar.class");
    var classDesc = ClassDesc.of("foo", "Bar");
    ClassFile.of()
        .buildTo(path, classDesc, classBuilder -> classBuilder
            .withFlags(AccessFlag.PUBLIC, AccessFlag.SUPER)
            .withSuperclass(ConstantDescs.CD_Object)
            .withMethod("method",
                MethodTypeDesc.of(ConstantDescs.CD_int),
                Modifier.PUBLIC | Modifier.STATIC,
                mb -> mb.withCode(codeBuilder -> codeBuilder
                    .iconst_1()
                    .ireturn()
                )
            )
        );
  }

  @Test
  public void evaluateEnvClassPathSucceeds() throws InterruptedException {
    var env = new Model.Snippet("/env --class-path .");
    var code = new Model.Snippet("""
        IO.println("ok");
        """);
    var program = new Model.Program(List.of(env, code));
    var execution = JShellRunner.evaluate(program, DEFAULT_TIMEOUT_MILLIS);
    var evaluations = execution.evaluations();
    assertEquals(2, evaluations.size());
    assertEquals(Model.Evaluation.Status.SUCCESS, evaluations.get(1).status());
    assertEquals("ok\n", evaluations.get(1).text());
  }

  @Test
  public void evaluateEnvClassPathIsVisibleToSubsequentSnippet(@TempDir Path dir) throws IOException, InterruptedException {
    createSyntheticClass(dir);
    var env = new Model.Snippet("/env --class-path " + dir);
    var code = new Model.Snippet("import foo.Bar");
    var program = new Model.Program(List.of(env, code));
    var execution = JShellRunner.evaluate(program, DEFAULT_TIMEOUT_MILLIS);
    var evaluation = execution.evaluations();
    assertEquals(2, evaluation.size());
    assertEquals(Model.Evaluation.Status.SUCCESS, evaluation.get(0).status());
    assertEquals("", evaluation.get(0).text());
    assertEquals(Model.Evaluation.Status.SUCCESS, evaluation.get(1).status());
    assertEquals("", evaluation.get(1).text());
  }

  @Test
  public void evaluateEnvClassCanBeCalledBySubsequentSnippet(@TempDir Path dir) throws IOException, InterruptedException {
    createSyntheticClass(dir);
    var env = new Model.Snippet("/env --class-path " + dir);
    var code = new Model.Snippet("IO.println(foo.Bar.method())");
    var program = new Model.Program(List.of(env, code));
    var execution = JShellRunner.evaluate(program, DEFAULT_TIMEOUT_MILLIS);
    var evaluation = execution.evaluations();
    assertEquals(2, evaluation.size());
    assertEquals(Model.Evaluation.Status.SUCCESS, evaluation.get(0).status());
    assertEquals("", evaluation.get(0).text());
    assertEquals(Model.Evaluation.Status.SUCCESS, evaluation.get(1).status());
    assertEquals("1\n", evaluation.get(1).text());
  }

  @Test
  public void evaluateEnvUnknownDirectiveReturnsError() throws InterruptedException {
    var snippet = new Model.Snippet("/env --unknown-option foo");
    var program = new Model.Program(List.of(snippet));
    var execution = JShellRunner.evaluate(program, DEFAULT_TIMEOUT_MILLIS);
    assertEquals(Model.Evaluation.Status.ERROR, execution.evaluations().getFirst().status());
  }

  @Test
  public void evaluateEnvUnknownDirectiveErrorMentionsDirective() throws InterruptedException {
    var snippet = new Model.Snippet("/env --unknown-option foo");
    var program = new Model.Program(List.of(snippet));
    var execution = JShellRunner.evaluate(program, DEFAULT_TIMEOUT_MILLIS);
    assertTrue(execution.evaluations().getFirst().text().contains("--unknown-option"));
  }

  @Test
  public void evaluateEnvModulePathReturnsError() throws InterruptedException {
    var snippet = new Model.Snippet("/env --module-path .");
    var program = new Model.Program(List.of(snippet));
    var execution = JShellRunner.evaluate(program, DEFAULT_TIMEOUT_MILLIS);
    assertEquals(Model.Evaluation.Status.ERROR, execution.evaluations().getFirst().status());
  }

  @Test
  public void evaluateEnvAddModulesReturnsError() throws InterruptedException {
    var snippet = new Model.Snippet("/env --add-modules java.sql");
    var program = new Model.Program(List.of(snippet));
    var execution = JShellRunner.evaluate(program, DEFAULT_TIMEOUT_MILLIS);
    assertEquals(Model.Evaluation.Status.ERROR, execution.evaluations().getFirst().status());
  }

  @Test
  public void evaluateEnvMissingArgumentReturnsError() throws InterruptedException {
    var snippet = new Model.Snippet("/env --class-path");
    var program = new Model.Program(List.of(snippet));
    var execution = JShellRunner.evaluate(program, DEFAULT_TIMEOUT_MILLIS);
    assertEquals(Model.Evaluation.Status.ERROR, execution.evaluations().getFirst().status());
  }

  @Test
  public void evaluateEnvNonEnvLineReturnsError() throws InterruptedException {
    var snippet = new Model.Snippet("""
        /env --class-path .
        int x = 1;
        """);
    var program = new Model.Program(List.of(snippet));
    var execution = JShellRunner.evaluate(program, DEFAULT_TIMEOUT_MILLIS);
    assertEquals(Model.Evaluation.Status.ERROR, execution.evaluations().getFirst().status());
  }

  @Test
  public void evaluateEnvErrorDoesNotAffectSubsequentSnippets() throws InterruptedException {
    var env = new Model.Snippet("/env --unknown-option foo");
    var code = new Model.Snippet("""
        IO.println("ok");
        """);
    var program = new Model.Program(List.of(env, code));
    var execution = JShellRunner.evaluate(program, DEFAULT_TIMEOUT_MILLIS);
    assertEquals(2, execution.evaluations().size());
    assertEquals(Model.Evaluation.Status.ERROR, execution.evaluations().get(0).status());
    assertEquals(Model.Evaluation.Status.SUCCESS, execution.evaluations().get(1).status());
    assertEquals("ok\n", execution.evaluations().get(1).text());
  }

  @Test
  public void evaluateEnvMultipleLinesSucceeds() throws InterruptedException {
    var snippet = new Model.Snippet("""
        /env --class-path .
        /env --class-path .
        """);
    var program = new Model.Program(List.of(snippet));
    var execution = JShellRunner.evaluate(program, DEFAULT_TIMEOUT_MILLIS);
    assertEquals(Model.Evaluation.Status.SUCCESS, execution.evaluations().getFirst().status());
  }
}