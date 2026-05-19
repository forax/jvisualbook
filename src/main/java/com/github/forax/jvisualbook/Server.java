package com.github.forax.jvisualbook;

import io.helidon.common.media.type.MediaTypes;
import io.helidon.http.HeaderNames;
import io.helidon.http.Status;
import io.helidon.http.media.MediaContext;
import io.helidon.http.media.jackson.JacksonSupport;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.http.HttpRouting;
import io.helidon.webserver.staticcontent.StaticContentFeature;

import java.io.IOException;
import java.io.ObjectInputFilter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/// HTTP server for JVisualBook, built on top of
/// [Helidon](https://helidon.io) SE.
///
/// The server exposes three groups of endpoints and serves the compiled React
/// front-end as static content from the classpath:
///
/// | Method | Path                       | Description                                     |
/// |--------|----------------------------|-------------------------------------------------|
/// | GET    | `/api/chapter`             | List all available chapters                     |
/// | GET    | `/api/chapter/{filename}`  | Fetch the parsed document for a chapter         |
/// | POST   | `/api/code`                | Evaluate a [Model.Program] object via JShell    |
/// | GET    | `/images/{filename}`       | Serve an image file from `./images/`            |
/// | GET    | `/**`                      | Static React front-end (classpath `/public`)    |
///
/// All JSON serialisation and deserialisation is handled by
/// [Jackson](https://github.com/FasterXML/jackson).
///
/// ## Security
///
/// Path traversal is prevented on both `/api/chapter/{filename}` and
/// `/images/{filename}` by [#validatePath], which checks that the resolved
/// path stays within its designated root directory.
/// Requests that escape the root are rejected with `403 Forbidden`.
///
/// Only a fixed set of MIME types recognized as images are accepted under `/images/`;
/// any other extension is rejected with `400 Bad Request`.
///
/// ## Entry points
///
/// The application entry point is [#main], which starts the server on port
/// `8080` bound to `localhost`. [#start] is the lower-level method used in
/// tests to start the server on an arbitrary port.
///
/// @see DocumentParser
/// @see JShellRunner
/// @see Model
public final class Server {

  /// The maximum number of seconds JShell is allowed to evaluate a [Model.Program]
  /// request before being forcibly stopped.
  ///
  /// @see JShellRunner#evaluate
  private static final int TIMEOUT_SECONDS = 5;

  /// This class is a utility class.
  private Server() {
    throw new AssertionError();
  }

  /// Extracts the file extension from `filename`.
  private static String extractExtension(String filename) {
    var dot = filename.lastIndexOf('.');
    return dot == -1 ? "" : filename.substring(dot + 1);
  }

  /// Resolves `filename` relative to `root` and verifies that the result stays
  /// within `root`, guarding against path traversal attacks.
  /// @return the validated `path` or null if the `path` is outside the `root` directory
  private static Path validatePath(Path root, String filename) {
    var base = root.normalize().toAbsolutePath();
    var target = base.resolve(filename).normalize().toAbsolutePath();
    if (!target.startsWith(base)) {
      return null;  // invalid path
    }
    return target;
  }

  /// Returns a sorted list of all chapters available in the current working
  /// directory.
  private static List<Model.Chapter> allChapters() throws IOException {
    try (var files = Files.list(Path.of("."))) {
      return files
          .map(file -> file.getFileName().toString())
          .filter(name -> name.endsWith(".jsh"))
          .map(name -> name.substring(0, name.length() - ".jsh".length()))
          .sorted()
          .map(Model.Chapter::new)
          .toList();
    }
  }

  /// Parses the `.jsh` file at `path` into a [Model.Document].
  private static Model.Document chapterDocument(Path path) throws IOException {
    return DocumentParser.parse(path);
  }

  /// Evaluates `code` by delegating to [JShellRunner#evaluate] with the
  /// server-wide [#TIMEOUT_SECONDS] limit.
  private static Model.Execution executeProgram(Model.Program program) throws InterruptedException {
    return JShellRunner.evaluate(program, TIMEOUT_SECONDS);
  }

  /// Registers all HTTP routes on `routing`.
  ///
  /// This method is package-private so that tests can reuse it directly without
  /// starting a real server via [#start].
  ///
  /// ### `GET /api/chapter`
  ///
  /// Returns a JSON array of [Model.Chapter] objects, one per `.jsh` file found
  /// in the current working directory, sorted alphabetically.
  ///
  /// ### `GET /api/chapter/{filename}`
  ///
  /// Parses the `.jsh` file whose stem matches `filename` and returns it as a
  /// JSON [Model.Document].
  ///
  /// ### `POST /api/code`
  ///
  /// Accepts a JSON [Model.Program] body, evaluates it via [JShellRunner], and
  /// returns a JSON [Model.Execution].
  ///
  /// ### `GET /images/{filename}`
  ///
  /// Serves image files from the `./images/` directory relative to the working
  /// directory.
  ///
  /// @param routing the routing builder to register routes on; must not be `null`
  static void routing(HttpRouting.Builder routing) {
    routing
        .get("/api/chapter", (_, res) -> {
          try {
            res.send(allChapters());
          } catch (IOException e) {
            res.status(Status.NOT_FOUND_404).send(Map.of("message", e.getMessage(), "kind", e.getClass().getSimpleName()));
          }
        })
        .get("/api/chapter/{filename}", (req, res) -> {
          var filename = req.path().pathParameters().get("filename");
          var target = validatePath(Path.of("."), filename + ".jsh");
          if (target == null) {
            res.status(Status.FORBIDDEN_403).send();
            return;
          }
          try {
            res.send(chapterDocument(target));
          } catch (IOException e) {
            res.status(Status.NOT_FOUND_404).send(Map.of("message", e.getMessage(), "kind", e.getClass().getSimpleName()));
          }
        })
        .post("/api/code", (req, res) -> {
          var program = req.content().as(Model.Program.class);
          var execution = executeProgram(program);
          res.send(execution);
        })
        .get("/images/{filename}", (req, res) -> {
          var filename = req.path().pathParameters().get("filename");
          var mediaTypeOpt = MediaTypes.detectType(filename);
          String media;
          if (mediaTypeOpt.isEmpty() || !((media = mediaTypeOpt.orElseThrow().text()).startsWith("image/"))) {
            res.status(Status.BAD_REQUEST_400).send(Map.of("extension", extractExtension(filename)));
            return;
          }
          var target = validatePath(Path.of("images"), filename);
          if (target == null) {
            res.status(Status.FORBIDDEN_403).send();
            return;
          }
          byte[] bytes;
          try {
            bytes = Files.readAllBytes(target);
          } catch (IOException e) {
            res.status(Status.NOT_FOUND_404).send(Map.of("message", e.getMessage(), "kind", e.getClass().getSimpleName()));
            return;
          }
          res.headers().set(HeaderNames.CONTENT_TYPE, media);
          res.send(bytes);
        });
  }

  /// Starts the Helidon web server on the given `port`, bound to `localhost`,
  /// and blocks until it is ready to accept connections.
  ///
  /// @param port the TCP port to listen on
  /// @return the running [WebServer] instance; never `null`
  static WebServer start(int port) {
    if (ObjectInputFilter.Config.getSerialFilter() == null) {
      ObjectInputFilter.Config.setSerialFilter(ObjectInputFilter.Config.createFilter("*"));
    }
    System.setProperty("helidon.serialFilter.failure.action", "ignore");
    return WebServer.builder()
        .port(port)
        .host("localhost")
        .mediaContext(MediaContext.builder()
            .addMediaSupport(JacksonSupport.create())
            .build())
        .addFeature(StaticContentFeature.builder()
            .addClasspath(cl -> cl.location("/public")
                .welcome("index.html")
                .context("/"))
            .build()
        )
        .routing(Server::routing)
        .build()
        .start();
  }

  /// Starts the server on port `8080` bound to `localhost` and
  /// serves chapters from the current working directory.
  ///
  /// The server runs until the process is terminated.
  static void main() {
    start(8080);
  }
}