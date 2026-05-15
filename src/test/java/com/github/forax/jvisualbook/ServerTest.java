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
import java.util.List;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.*;

@RoutingTest
public class ServerTest {
  private static final ObjectMapper MAPPER = new ObjectMapper()
      .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

  private final Http1Client client;

  public ServerTest(Http1Client client) {
    this.client = Objects.requireNonNull(client);
  }

  @SetUpRoute
  public static void routing(HttpRouting.Builder routing) {
    Server.routing(Objects.requireNonNull(routing));
  }

  @Test
  public void testGetChapters() {
    try (var response = client.get("/api/chapter").request()) {
      assertEquals(Status.OK_200, response.status());
    }
  }

  @Test
  public void testGetChaptersReturnsJsonArray() throws IOException {
    record Chapter(String name) {}
    try (var response = client.get("/api/chapter").request()) {
      assertEquals(Status.OK_200, response.status());
      var chapters = MAPPER.readValue(response.entity().as(String.class),
          MAPPER.getTypeFactory().constructCollectionType(List.class, Chapter.class));
      assertNotNull(chapters);
    }
  }

  @Test
  public void testGetChaptersItemsHaveNameField() throws IOException {
    record Chapter(String name) {}
    try (var response = client.get("/api/chapter").request()) {
      assertEquals(Status.OK_200, response.status());
      List<Chapter> chapters = MAPPER.readValue(response.entity().as(String.class),
          MAPPER.getTypeFactory().constructCollectionType(List.class, Chapter.class));
      for (var chapter : chapters) {
        assertNotNull(chapter.name(), "Each chapter must have a non-null 'name'");
      }
    }
  }

  @Test
  public void testChapterPathTraversal() {
    // Helidon normalizes the URL so traversal sequences are collapsed before
    // reaching the handler; the sanitized path won't match any .jsh file → 404.
    try (var response = client.get("/api/chapter/../../etc/passwd").request()) {
      var status = response.status();
      assertTrue(
          status.equals(Status.FORBIDDEN_403) || status.equals(Status.NOT_FOUND_404),
          "Expected 403 or 404, got: " + status
      );
    }
  }

  @Test
  public void testChapterNotFound() {
    try (var response = client.get("/api/chapter/nonexistent").request()) {
      assertEquals(Status.NOT_FOUND_404, response.status());
    }
  }

  @Test
  public void testChapterNotFoundBodyHasMessageAndKind() throws IOException {
    record ErrorBody(String message, String kind) {}
    try (var response = client.get("/api/chapter/nonexistent").request()) {
      assertEquals(Status.NOT_FOUND_404, response.status());
      var error = MAPPER.readValue(response.entity().as(String.class), ErrorBody.class);
      assertNotNull(error.message());
      assertNotNull(error.kind());
    }
  }

  @Test
  public void testImageBadExtension() {
    try (var response = client.get("/images/file.exe").request()) {
      assertEquals(Status.BAD_REQUEST_400, response.status());
    }
  }

  @Test
  public void testImageBadExtensionBodyHasExtensionField() throws IOException {
    record ErrorBody(String extension) {}
    try (var response = client.get("/images/file.exe").request()) {
      var error = MAPPER.readValue(response.entity().as(String.class), ErrorBody.class);
      assertEquals("exe", error.extension());
    }
  }

  @Test
  public void testImageNotFound() {
    try (var response = client.get("/images/nonexistent.png").request()) {
      assertEquals(Status.NOT_FOUND_404, response.status());
    }
  }
}