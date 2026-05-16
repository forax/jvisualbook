package com.github.forax.jvisualbook;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.helidon.webserver.WebServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public final class CodeEndpointIT {

  private static final ObjectMapper MAPPER = new ObjectMapper()
      .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

  private static final HttpClient HTTP = HttpClient.newHttpClient();
  private static final String BASE_URL = "http://localhost:8083";

  record Evaluation(String status, String text) {}
  record Execution(List<Evaluation> evaluations) {}

  private static WebServer server;

  @BeforeAll
  public static void startServer() {
    server = Server.start(8083);
  }

  @AfterAll
  public static void stopServer() {
    server.stop();
    server = null;
  }

  // -- helpers

  private static String body(String... snippetCodes) throws IOException {
    var snippets = Arrays.stream(snippetCodes)
        .map(Model.Snippet::new)
        .toList();
    return MAPPER.writeValueAsString(new Model.Code(snippets));
  }

  private static Execution post(String body) throws IOException {
    var request = HttpRequest.newBuilder()
        .uri(URI.create(BASE_URL + "/api/code"))
        .header("Content-Type", "application/json")
        .POST(HttpRequest.BodyPublishers.ofString(body))
        .build();
    HttpResponse<String> response;
    try {
      response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
    } catch (InterruptedException e) {
      throw new AssertionError(e);
    }
    assertEquals(200, response.statusCode());
    return MAPPER.readValue(response.body(), Execution.class);
  }


  // -- tests

  @Test
  public void emptySnippetListReturnsEmptyEvaluations() throws IOException {
    var body = MAPPER.writeValueAsString(new Model.Code(List.of()));
    var execution = post(body);
    assertEquals(0, execution.evaluations().size());
  }

  @Test
  public void singleSuccessfulSnippet() throws IOException {
    var execution = post(body("1 + 1;"));
    assertEquals(1, execution.evaluations().size());
    assertEquals("SUCCESS", execution.evaluations().getFirst().status());
  }

  @Test
  public void successfulSnippetWithNoOutputHasEmptyText() throws IOException {
    var execution = post(body("int y = 5;"));
    var evaluation = execution.evaluations().getFirst();
    assertEquals("SUCCESS", evaluation.status());
    assertEquals("", evaluation.text());
  }

  @Test
  public void printlnCapturesOutput() throws IOException {
    var execution = post(body("""
        IO.println("hello");
        """));
    var evaluation = execution.evaluations().getFirst();
    assertEquals("SUCCESS", evaluation.status());
    assertEquals("hello\n", evaluation.text());
  }

  @Test
  public void stderrCapturedToo() throws IOException {
    var execution = post(body("""
        System.err.println("oops");
        """));
    var evaluation = execution.evaluations().getFirst();
    assertEquals("SUCCESS", evaluation.status());
    assertEquals("oops\n", evaluation.text());
  }

  @Test
  public void multipleStatementsInOneSnippetConcatenatesOutput() throws IOException {
    var execution = post(body("""
        System.out.print("x");
        System.out.print("y");
        """));
    var evaluation = execution.evaluations().getFirst();
    assertEquals("SUCCESS", evaluation.status());
    assertEquals("xy", evaluation.text());
  }

  @Test
  public void syntaxErrorReturnsError() throws IOException {
    var execution = post(body("int x = ;"));
    var evaluation = execution.evaluations().getFirst();
    assertEquals("ERROR", evaluation.status());
    assertFalse(evaluation.text().isEmpty());
  }

  @Test
  public void undefinedVariableReturnsError() throws IOException {
    var execution = post(body("int x = undefinedVar;"));
    assertEquals("ERROR", execution.evaluations().getFirst().status());
  }

  @Test
  public void runtimeExceptionReturnsError() throws IOException {
    var execution = post(body("""
        throw new RuntimeException("boom");
        """));
    var evaluation = execution.evaluations().getFirst();
    assertEquals("ERROR", evaluation.status());
    assertTrue(evaluation.text().contains("RuntimeException"));
  }

  @Test
  public void userDefinedExceptionReturnsError() throws IOException {
    var execution = post(body("""
      class MyException extends RuntimeException {}
      throw new MyException();
      """));
    var evaluation = execution.evaluations().getFirst();
    assertEquals("ERROR", evaluation.status());
    assertTrue(evaluation.text().contains("MyException"));
  }

  @Test
  public void missingClosingParenthesisReturnsError() throws IOException {
    var execution = post(body("""
        IO.println("hello";
        """));
    assertEquals("ERROR", execution.evaluations().getFirst().status());
  }

  @Test
  public void unclosedStringLiteralReturnsError() throws IOException {
    var execution = post(body("""
        String s = "hello;
        """));
    assertEquals("ERROR", execution.evaluations().getFirst().status());
  }

  @Test
  public void missingReferenceReturnsError() throws IOException {
    var execution = post(body("IO.println(a);"));
    assertEquals("ERROR", execution.evaluations().getFirst().status());
  }

  @Test
  public void multipleSnippetsEachHaveIndependentOutput() throws IOException {
    var execution = post(body("IO.println(\"first\");", "IO.println(\"second\");"));
    assertEquals(2, execution.evaluations().size());
    assertEquals("first\n",  execution.evaluations().get(0).text());
    assertEquals("second\n", execution.evaluations().get(1).text());
  }

  @Test
  public void outputIsResetBetweenSnippets() throws IOException {
    var execution = post(body("IO.println(\"a\");", "IO.println(\"b\");"));
    assertFalse(execution.evaluations().get(1).text().contains("a"));
  }

  @Test
  public void errorInFirstSnippetDoesNotPreventSecondFromSucceeding() throws IOException {
    var execution = post(body("int x = !!;", "IO.println(\"ok\");"));
    assertEquals(2, execution.evaluations().size());
    assertEquals("ERROR",   execution.evaluations().get(0).status());
    assertEquals("SUCCESS", execution.evaluations().get(1).status());
    assertEquals("ok\n",    execution.evaluations().get(1).text());
  }

  @Test
  public void variablesDeclaredInEarlierSnippetAreVisibleInLaterSnippet() throws IOException {
    var execution = post(body("int x = 42;", "IO.println(x);"));
    assertEquals("SUCCESS", execution.evaluations().get(0).status());
    assertEquals("SUCCESS", execution.evaluations().get(1).status());
    assertEquals("42\n",    execution.evaluations().get(1).text());
  }

  @Test
  public void methodDefinedInEarlierSnippetCallableInLaterSnippet() throws IOException {
    var execution = post(body(
        "int square(int n) { return n * n; }",
        "IO.println(square(4));"));
    assertEquals("SUCCESS", execution.evaluations().get(1).status());
    assertEquals("16\n",    execution.evaluations().get(1).text());
  }

  @Test
  public void infiniteLoopTimesOutAndAllSnippetsAreErrors() {
    assertTimeoutPreemptively(Duration.ofSeconds(10), () -> {
      var execution = post(body("for(;;);", "IO.println(\"never\");"));
      assertEquals(2, execution.evaluations().size());
      assertTrue(execution.evaluations().stream()
          .allMatch(e -> "ERROR".equals(e.status())));
    });
  }

  @Test
  public void timeoutErrorMessageMentionsTimeout() {
    assertTimeoutPreemptively(Duration.ofSeconds(10), () -> {
      var execution = post(body("while (true) {}"));
      var evaluation = execution.evaluations().getFirst();
      assertEquals("ERROR", evaluation.status());
      assertTrue(evaluation.text().contains("timed out"));
    });
  }
}