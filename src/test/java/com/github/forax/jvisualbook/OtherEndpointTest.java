package com.github.forax.jvisualbook;

import com.fasterxml.jackson.core.type.TypeReference;
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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/// Tests all endpoints but /api/code
public final class OtherEndpointTest {

  private static final ObjectMapper MAPPER = new ObjectMapper()
      .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

  private static final HttpClient HTTP = HttpClient.newHttpClient();
  private static final String BASE_URL = "http://localhost:8084";

  record Chapter(String name) {}
  record Section(String title, List<?> contents) {}
  record Document(List<Section> sections) {}
  record ErrorBody(String message, String kind) {}
  record ExtensionError(String extension) {}

  private static WebServer server;

  @BeforeAll
  static void startServer() {
    server = Server.start(8084, Path.of("."), 5_000);
  }

  @AfterAll
  static void stopServer() {
    server.stop();
    server = null;
  }

  // -- helpers

  private static HttpResponse<String> get(String path) throws IOException {
    var request = HttpRequest.newBuilder()
        .uri(URI.create(BASE_URL + path))
        .GET()
        .build();
    try {
      return HTTP.send(request, HttpResponse.BodyHandlers.ofString());
    } catch (InterruptedException e) {
      throw new AssertionError(e);
    }
  }

  private static HttpResponse<byte[]> getBytes(String path) throws IOException {
    var request = HttpRequest.newBuilder()
        .uri(URI.create(BASE_URL + path))
        .GET()
        .build();
    try {
      return HTTP.send(request, HttpResponse.BodyHandlers.ofByteArray());
    } catch (InterruptedException e) {
      throw new AssertionError(e);
    }
  }


  // -- /api/chapter --

  @Test
  public void getChaptersReturns200() throws IOException {
    var response = get("/api/chapter");
    assertEquals(200, response.statusCode());
  }

  @Test
  public void getChaptersReturnsJsonArray() throws IOException {
    var response = get("/api/chapter");
    assertEquals(200, response.statusCode());
    var chapters = MAPPER.readValue(response.body(),
        new TypeReference<List<Chapter>>() {});
    assertNotNull(chapters);
  }

  @Test
  public void getChaptersItemsAllHaveNameField() throws IOException {
    var response = get("/api/chapter");
    assertEquals(200, response.statusCode());
    var chapters = MAPPER.readValue(response.body(),
        new TypeReference<List<Chapter>>() {});
    for (var chapter : chapters) {
      assertNotNull(chapter.name());
      assertFalse(chapter.name().isBlank());
    }
  }

  @Test
  public void getChaptersNamesHaveNoJshExtension() throws IOException {
    var response = get("/api/chapter");
    assertEquals(200, response.statusCode());
    var chapters = MAPPER.readValue(response.body(),
        new TypeReference<List<Chapter>>() {});
    for (var chapter : chapters) {
      assertFalse(chapter.name().endsWith(".jsh"),
          "Chapter name should not include the .jsh extension: " + chapter.name());
    }
  }

  @Test
  public void getChaptersAreSortedAlphabetically() throws IOException {
    var response = get("/api/chapter");
    assertEquals(200, response.statusCode());
    var chapters = MAPPER.readValue(response.body(),
        new TypeReference<List<Chapter>>() {});
    var names = chapters.stream().map(Chapter::name).toList();
    var sorted = names.stream().sorted().toList();
    assertEquals(sorted, names, "Chapters should be returned in alphabetical order");
  }

  // -- /api/chapter/{filename} --

  @Test
  public void getUnknownChapterReturns404() throws IOException {
    var response = get("/api/chapter/nonexistent");
    assertEquals(404, response.statusCode());
  }

  @Test
  public void getUnknownChapterBodyHasMessageAndKind() throws IOException {
    var response = get("/api/chapter/nonexistent");
    assertEquals(404, response.statusCode());
    var error = MAPPER.readValue(response.body(), ErrorBody.class);
    assertNotNull(error.message());
    assertNotNull(error.kind());
  }

  @Test
  public void getChapterPathTraversalReturnsForbiddenOrNotFound() throws IOException {
    var response = get("/api/chapter/../../etc/passwd");
    assertTrue(
        response.statusCode() == 403 || response.statusCode() == 404,
        "Expected 403 or 404, got: " + response.statusCode());
  }

  @Test
  public void getKnownChapterReturns200() throws IOException {
    var chaptersResponse = get("/api/chapter");
    assertEquals(200, chaptersResponse.statusCode());
    var chapters = MAPPER.readValue(chaptersResponse.body(),
        new TypeReference<List<Chapter>>() {});
    assertFalse(chapters.isEmpty(), "jvisualbook folder should contain at least one chapter");
    var first = chapters.getFirst();
    var response = get("/api/chapter/" + first.name());
    assertEquals(200, response.statusCode());
  }

  @Test
  public void getKnownChapterReturnsSections() throws IOException {
    var chaptersResponse = get("/api/chapter");
    assertEquals(200, chaptersResponse.statusCode());
    var chapters = MAPPER.readValue(chaptersResponse.body(),
        new TypeReference<List<Chapter>>() {});
    assertFalse(chapters.isEmpty(), "jvisualbook folder should contain at least one chapter");
    var first = chapters.getFirst();
    var response = get("/api/chapter/" + first.name());
    assertEquals(200, response.statusCode());
    var doc = MAPPER.readValue(response.body(), Document.class);
    assertNotNull(doc.sections());
  }


  // -- /images/{filename} --

  @Test
  public void getImageWithUnsupportedExtensionReturns400() throws IOException {
    var response = get("/images/file.exe");
    assertEquals(400, response.statusCode());
  }

  @Test
  public void getImageWithUnsupportedExtensionBodyHasExtensionField() throws IOException {
    var response = get("/images/file2.exe");
    assertEquals(400, response.statusCode());
    var error = MAPPER.readValue(response.body(), ExtensionError.class);
    assertEquals("exe", error.extension());
  }

  @Test
  public void getImageWithNoExtensionReturns400() throws IOException {
    var response = get("/images/noextension");
    assertEquals(400, response.statusCode());
  }

  @Test
  public void getMissingPngReturns404() throws IOException {
    var response = get("/images/definitely-does-not-exist.png");
    assertEquals(404, response.statusCode());
  }

  @Test
  public void getMissingJpgReturns404() throws IOException {
    var response = get("/images/definitely-does-not-exist.jpg");
    assertEquals(404, response.statusCode());
  }

  @Test
  public void getMissingGifReturns404() throws IOException {
    var response = get("/images/definitely-does-not-exist.gif");
    assertEquals(404, response.statusCode());
  }

  @Test
  public void getExistingPngReturns200WithCorrectContentType() throws IOException {
    var response = getBytes("/images/jvisualbook.png");
    assertEquals(200, response.statusCode());
    var contentType = response.headers().firstValue("Content-Type").orElse("");
    assertEquals("image/png", contentType);
  }

  @Test
  public void getExistingPngReturnsCorrectBytes() throws IOException {
    var response = getBytes("/images/jvisualbook.png");
    assertEquals(200, response.statusCode());
    var expected = Files.readAllBytes(Paths.get("./images/jvisualbook.png"));
    assertArrayEquals(expected, response.body());
  }

  @Test
  public void getImagePathTraversalReturnsForbiddenOrNotFound() throws IOException {
    var response = get("/images/../../etc/passwd");
    assertTrue(
        response.statusCode() == 403 || response.statusCode() == 404,
        "Expected 403 or 404, got: " + response.statusCode());
  }
}