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
import java.util.Locale;
import java.util.Map;

public final class Server {
  private static final int TIMEOUT_SECONDS = 5;

  private static String extractExtension(String filename) {
    var dot = filename.lastIndexOf('.');
    return dot == -1 ? "" : filename.substring(dot + 1).toLowerCase(Locale.ROOT);
  }

  private static Path validatePath(Path root, String filename) throws IOException {
    var base = root.normalize().toAbsolutePath();
    var target = base.resolve(filename).normalize().toAbsolutePath();
    if (!target.startsWith(base)) {
      throw new IOException("invalid path: " + target);
    }
    return target;
  }

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

  private static Model.Document chapterDocument(Path path) throws IOException {
    return DocumentParser.parse(path);
  }

  private static Model.Execution executeCode(Model.Code code) {
    return JShellRunner.evaluate(code, TIMEOUT_SECONDS);
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
          var mediaTypeOpt = MediaTypes.detectType(filename);
          String media;
          if (mediaTypeOpt.isEmpty() || !((media = mediaTypeOpt.orElseThrow().text()).startsWith("image/"))) {
            res.status(Status.BAD_REQUEST_400).send(Map.of("extension", extractExtension(filename)));
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

  static void main() {
    start(8080);
  }
}