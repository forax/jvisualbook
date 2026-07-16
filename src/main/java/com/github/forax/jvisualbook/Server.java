package com.github.forax.jvisualbook;

import io.helidon.common.media.type.MediaTypes;
import io.helidon.http.HeaderNames;
import io.helidon.http.Status;
import io.helidon.http.media.MediaContext;
import io.helidon.http.media.jackson.JacksonSupport;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.http.HttpRouting;
import io.helidon.webserver.http.ServerRequest;
import io.helidon.webserver.staticcontent.StaticContentFeature;

import java.io.IOException;
import java.io.ObjectInputFilter;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
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
/// | GET    | `/images/{filename}`       | Serve an image file from `<dir>/images/`        |
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
/// The application entry point is [#main], which starts the server bound to `localhost`.
/// [#start(int, Path, int)] is the lower-level method used to start the server
/// on an arbitrary port, with a directory to scan and a specified timeout in milliseconds.
///
/// ## Recognized command line options:
/// - `--port <port>`   TCP port (default: 8080)
/// - `--dir <path>`    Directory for `.jsh` chapters (default: `.`)
/// - `--timeout <ms>`  JShell timeout in milliseconds (default: 5000)
/// - `--help`          Print help and exit
///
/// @see DocumentParser
/// @see JShellRunner
/// @see Model
public final class Server {
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

  /// Mitigates cross-site request forgery from malicious web pages.
  /// @return `true` if the request should be allowed
  private static boolean isOriginAllowed(ServerRequest req, int port) {
    var origin = req.headers().first(HeaderNames.ORIGIN).orElse(null);
    var expectedOrigin = "http://localhost:" + port;
    return expectedOrigin.equals(origin);
  }

  /// Returns a sorted list of all chapters available in `dir`.
  private static List<Model.Chapter> allChapters(Path dir) throws IOException {
    try (var files = Files.list(dir)) {
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

  /// Evaluates `code` by delegating to [JShellRunner#evaluate] with `timeoutMillis`.
  private static Model.Execution executeProgram(Model.Program program, int timeoutMillis) throws InterruptedException {
    return JShellRunner.evaluate(program, timeoutMillis);
  }

  /// Registers all HTTP routes on `routing`, serving chapters from `dir` with
  /// the given `timeoutMillis`.
  ///
  /// @param routing       the routing builder to register routes on; must not be `null`
  /// @param port          the TCP port to listen on
  /// @param dir           the directory to scan for `.jsh` chapter files
  /// @param timeoutMillis the JShell evaluation timeout in milliseconds
  static void routing(HttpRouting.Builder routing, int port, Path dir, int timeoutMillis) {
    routing
        .get("/api/chapter", (_, res) -> {
          try {
            res.send(allChapters(dir));
          } catch (IOException e) {
            res.status(Status.NOT_FOUND_404)
                .send(Map.of("message", e.getMessage(), "kind", e.getClass().getSimpleName()));
          }
        })
        .get("/api/chapter/{filename}", (req, res) -> {
          var filename = req.path().pathParameters().get("filename");
          var target = validatePath(dir, filename + ".jsh");
          if (target == null) {
            res.status(Status.FORBIDDEN_403).send();
            return;
          }
          try {
            res.send(chapterDocument(target));
          } catch (IOException e) {
            res.status(Status.NOT_FOUND_404)
                .send(Map.of("message", e.getMessage(), "kind", e.getClass().getSimpleName()));
          }
        })
        .post("/api/code", (req, res) -> {
          if (!isOriginAllowed(req, port)) {
            res.status(Status.FORBIDDEN_403).send();
            return;
          }
          var program = req.content().as(Model.Program.class);
          var execution = executeProgram(program, timeoutMillis);
          res.send(execution);
        })
        .get("/images/{filename}", (req, res) -> {
          var filename = req.path().pathParameters().get("filename");
          var mediaTypeOpt = MediaTypes.detectType(filename);
          String media;
          if (mediaTypeOpt.isEmpty() || !((media = mediaTypeOpt.orElseThrow().text()).startsWith("image/"))) {
            res.status(Status.BAD_REQUEST_400)
                .send(Map.of("extension", extractExtension(filename)));
            return;
          }
          var target = validatePath(dir.resolve("images"), filename);
          if (target == null) {
            res.status(Status.FORBIDDEN_403).send();
            return;
          }
          try (var input = Files.newInputStream(target)) {
            res.header(HeaderNames.CONTENT_TYPE, media);
            res.contentLength(Files.size(target));
            try (var output = res.outputStream()) {
              input.transferTo(output);
            }
          } catch (IOException e) {
            if (!res.isSent()) {
              res.status(Status.NOT_FOUND_404)
                  .send(Map.of("message", e.getMessage(), "kind", e.getClass().getSimpleName()));
              return;
            }
            throw e;
          }
        });
  }

  /// Starts the Helidon web server bound to `localhost`, on the given `port`,
  /// serving chapters from `dir`, and blocks until it is ready to accept connections.
  ///
  /// @param port          the TCP port to listen on
  /// @param dir           the directory to scan for `.jsh` chapter files
  /// @param timeoutMillis the JShell evaluation timeout in milliseconds
  /// @return the running [WebServer] instance
  static WebServer start(int port, Path dir, int timeoutMillis) {
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
        .routing(routing -> routing(routing, port, dir, timeoutMillis))
        .build()
        .start();
  }

  /// Exit with code 1, prints a message and the Help on stderr.
  private static RuntimeException exitWith(String message) {
    return exitWith(message, System.err, 1);
  }

  /// Exit with a message and prints the Help.
  private static RuntimeException exitWith(String message, PrintStream out, int exitCode) {
    out.println(message);
    out.println();
    out.print("""
      Usage: java -jar jvisualbook.jar [OPTIONS]

      Options:
        --port <port>       TCP port to listen on (default: 8080)
        --dir <path>        Directory to scan for .jsh chapter files (default: .)
        --timeout <ms>      JShell evaluation timeout in milliseconds (default: 5000)
        --help              Show this help message and exit

      Examples:
        java -jar jvisualbook.jar
        java -jar jvisualbook.jar --port 9090 --dir /home/user/notebooks
        java -jar jvisualbook.jar --timeout 10000
      """);
    System.exit(exitCode);
    throw new AssertionError();
  }

  /// Configuration derived from CLI arguments.
  private record Config(int port, Path dir, int timeoutMillis) {}

  /// Parses `args` and returns a [Config], or prints a help/error
  /// message and calls [System#exit] if the arguments are invalid.
  private static Config parseConfig(String[] args) {
    var port = 8080;
    var dir = Path.of(".");
    var timeoutMillis = 5_000;

    for (var i = 0; i < args.length; i++) {
      switch (args[i]) {
        case "--help" -> throw exitWith("Help:", System.out, 0);
        case "--port" -> {
          if (i + 1 >= args.length) {
            throw exitWith("Error: --port requires a value");
          }
          var portText = args[++i];
          try {
            port = Integer.parseInt(portText);
          } catch (NumberFormatException _) {
            throw exitWith("Error: --port value must be an integer, got: " + portText);
          }
          if (port < 1 || port > 65535) {
            throw exitWith("Error: --port value must be between 1 and 65535, got: " + port);
          }
        }
        case "--dir" -> {
          if (i + 1 >= args.length) {
            throw exitWith("Error: --dir requires a value");
          }
          var dirText = args[++i];
          try {
            dir = Path.of(dirText);
          } catch (InvalidPathException _) {
            throw exitWith("Error: --dir value is not a valid path: " + dirText);
          }
          if (!Files.isDirectory(dir)) {
            throw exitWith("Error: --dir path does not exist or is not a directory: " + dirText);
          }
        }
        case "--timeout" -> {
          if (i + 1 >= args.length) {
            throw exitWith("Error: --timeout requires a value");
          }
          var timeoutText = args[++i];
          try {
            timeoutMillis = Integer.parseInt(timeoutText);
          } catch (NumberFormatException _) {
            throw exitWith("Error: --timeout value must be an integer, got: " + timeoutText);
          }
          if (timeoutMillis <= 0) {
            throw exitWith("Error: --timeout value must be positive, got: " + timeoutMillis);
          }
        }
        default -> throw exitWith("Error: Unknown option: " + args[i]);
      }
    }

    return new Config(port, dir, timeoutMillis);
  }

  /// Parse the command-line arguments and starts the server.
  static void main(String[] args) {
    var config = parseConfig(args);
    IO.println("Starting server at http://localhost:" + config.port() +
        " with dir=" + config.dir() + ", timeout=" + config.timeoutMillis() + "ms");
    start(config.port(), config.dir(), config.timeoutMillis());
  }
}