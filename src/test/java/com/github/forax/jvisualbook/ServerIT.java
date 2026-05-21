package com.github.forax.jvisualbook;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.helidon.common.media.type.MediaTypes;
import io.helidon.http.Status;
import io.helidon.webclient.http1.Http1Client;
import io.helidon.webserver.http.HttpRouting;
import io.helidon.webserver.testing.junit5.RoutingTest;
import io.helidon.webserver.testing.junit5.SetUpRoute;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@RoutingTest
public class ServerIT {
  private static final ObjectMapper MAPPER = new ObjectMapper()
      .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

  private final Http1Client client;

  public ServerIT(Http1Client client) {
    this.client = Objects.requireNonNull(client);
  }

  @SetUpRoute
  public static void routing(HttpRouting.Builder routing) {
    Server.routing(Objects.requireNonNull(routing), Path.of("."), 5_000);
  }

  @Test
  public void testPostCodeSuccess() throws IOException {
    record Evaluation(String status, String text) {}
    record Execution(List<Evaluation> evaluations) {}
    var body = MAPPER.writeValueAsString(
        new Model.Program(List.of(new Model.Snippet("""
            IO.println("hello");
            """))));

    try (var response = client.post("/api/code")
        .contentType(MediaTypes.APPLICATION_JSON)
        .submit(body)) {
      assertEquals(Status.OK_200, response.status());
      var execution = MAPPER.readValue(response.entity().as(String.class), Execution.class);
      assertEquals(1, execution.evaluations().size());
      var eval = execution.evaluations().getFirst();
      assertEquals("SUCCESS", eval.status());
      assertEquals("hello\n", eval.text());
    }
  }

  @Test
  public void testPostCodeCompileError() throws IOException {
    record Evaluation(String status, String text) {}
    record Execution(List<Evaluation> evaluations) {}
    var body = MAPPER.writeValueAsString(
        new Model.Program(List.of(new Model.Snippet("this is not valid java;"))));

    try (var response = client.post("/api/code")
        .contentType(MediaTypes.APPLICATION_JSON)
        .submit(body)) {
      assertEquals(Status.OK_200, response.status());
      var execution = MAPPER.readValue(response.entity().as(String.class), Execution.class);
      var eval = execution.evaluations().getFirst();
      assertEquals("ERROR", eval.status());
      assertFalse(eval.text().isEmpty());
    }
  }

  @Test
  public void testPostCodeRuntimeException() throws IOException {
    record Evaluation(String status, String text) {}
    record Execution(List<Evaluation> evaluations) {}
    var body = MAPPER.writeValueAsString(
        new Model.Program(List.of(new Model.Snippet("throw new RuntimeException(\"boom\");"))));

    try (var response = client.post("/api/code")
        .contentType(MediaTypes.APPLICATION_JSON)
        .submit(body)) {
      assertEquals(Status.OK_200, response.status());
      var execution = MAPPER.readValue(response.entity().as(String.class), Execution.class);
      var eval = execution.evaluations().getFirst();
      assertEquals("ERROR", eval.status());
      assertTrue(eval.text().contains("RuntimeException"));
    }
  }

  @Test
  public void testPostCodeMultipleSnippets() throws IOException {
    record Evaluation(String status, String text) {}
    record Execution(List<Evaluation> evaluations) {}
    var body = MAPPER.writeValueAsString(new Model.Program(List.of(
        new Model.Snippet("System.out.println(\"first\");"),
        new Model.Snippet("System.out.println(\"second\");")
    )));

    try (var response = client.post("/api/code")
        .contentType(MediaTypes.APPLICATION_JSON)
        .submit(body)) {
      assertEquals(Status.OK_200, response.status());
      var execution = MAPPER.readValue(response.entity().as(String.class), Execution.class);
      assertEquals(2, execution.evaluations().size());
      assertEquals("SUCCESS", execution.evaluations().get(0).status());
      assertEquals("first\n",  execution.evaluations().get(0).text());
      assertEquals("SUCCESS", execution.evaluations().get(1).status());
      assertEquals("second\n", execution.evaluations().get(1).text());
    }
  }

  @Test
  public void testPostCodeEmptySnippetList() throws IOException {
    record Execution(List<?> evaluations) {}
    var body = MAPPER.writeValueAsString(new Model.Program(List.of()));

    try (var response = client.post("/api/code")
        .contentType(MediaTypes.APPLICATION_JSON)
        .submit(body)) {
      assertEquals(Status.OK_200, response.status());
      var execution = MAPPER.readValue(response.entity().as(String.class), Execution.class);
      assertEquals(0, execution.evaluations().size());
    }
  }
}