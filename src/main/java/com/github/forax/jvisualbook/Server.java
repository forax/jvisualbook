package com.github.forax.jvisualbook;

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

public final class Server {
  private record Chapter(String name) {}

  private static final Map<String, String> MEDIA_TYPES = Map.of(
      "png",  "image/png",
      "jpg",  "image/jpeg",
      "jpeg", "image/jpeg",
      "gif",  "image/gif"
  );

  private static String removeExtension(String filename) {
    return filename.substring(0, filename.lastIndexOf('.'));
  }

  private static String extractExtension(String filename) {
    var dot = filename.lastIndexOf('.');
    return dot == -1 ? "" : filename.substring(dot + 1).toLowerCase();
  }

  private static Path validatePath(Path root, String filename) throws IOException {
    var base = root.normalize().toAbsolutePath();
    var target = base.resolve(filename).normalize().toAbsolutePath();
    if (!target.startsWith(base)) {
      throw new IOException("invalid path: " + target);
    }
    return target;
  }

  private static List<Chapter> allChapters() throws IOException {
    try (var files = Files.list(Path.of("."))) {
      return files
          .map(f -> f.getFileName().toString())
          .filter(n -> n.endsWith(".jsh"))
          .map(Server::removeExtension)
          .sorted()
          .map(Chapter::new)
          .toList();
    }
  }

  private static Model.Document chapterDocument(Path path) throws IOException {
    return DocumentParser.parse(path);
  }

  private static Model.Execution executeCode(Model.Code code) {
    return JShellRunner.evaluate(code);
  }

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
          Path target;
          try {
            target = validatePath(Path.of("."), filename + ".jsh");
          } catch (IOException e) {
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
          var code = req.content().as(Model.Code.class);
          var execution = executeCode(code);
          res.send(execution);
        })
        .get("/images/{filename}", (req, res) -> {
          var filename = req.path().pathParameters().get("filename");
          var ext = extractExtension(filename);
          var media = MEDIA_TYPES.get(ext);
          if (media == null) {
            res.status(Status.BAD_REQUEST_400).send(Map.of("extension", ext));
            return;
          }
          Path target;
          try {
            target = validatePath(Path.of("images"), filename);
          } catch (IOException e) {
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

  static void main() {
    ObjectInputFilter.Config.setSerialFilter(ObjectInputFilter.Config.createFilter("*"));
    System.setProperty("helidon.serialFilter.failure.action", "warn");

    WebServer.builder()
        .port(8080)
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
}