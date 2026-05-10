package com.github.forax.jvisualbook;

import io.helidon.http.Status;
import io.helidon.http.media.MediaContext;
import io.helidon.http.media.jackson.JacksonSupport;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.staticcontent.StaticContentFeature;

import java.io.File;
import java.io.IOException;
import java.io.ObjectInputFilter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public final class Server {
  private record Chapter(String name) {}

  private static String removeExtension(String filename) {
    return filename.substring(0, filename.lastIndexOf('.'));
  }

  private static List<Chapter> allChapters() throws IOException {
    try(var files = Files.list(Path.of("."))) {
      return files
          .map(f -> f.getFileName().toString())
          .filter(n -> n.endsWith(".jsh"))
          .map(Server::removeExtension)
          .sorted()
          .map(Chapter::new)
          .toList();
    }
  }

  private static Model.Document chapterDocument(String name) throws IOException {
    var path = Path.of(".", name + ".jsh");
    return DocumentParser.parse(path);
  }

  private static Model.Execution executeCode(Model.Code code) {
    return JShellRunner.evaluate(code);
  }

  static void main() {
    // Helidon do not want to allow all classes to be serialized but jshell requires that
    // -Djdk.serialFilter=* -Dhelidon.serialFilter.failure.action=warn
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
        .routing(it -> it
            .get("/api/chapter", (_, res) -> {
              try {
                res.send(allChapters());
              } catch (IOException e) {
                res.status(Status.NOT_FOUND_404).send(Map.of("message", e.getMessage(), "kind", e.getClass().getSimpleName()));
              }
            })
            .get("/api/chapter/{id}", (req, res) -> {
              var id = req.path().pathParameters().get("id");
              if (id.contains(".") || id.contains(File.separator)) {
                res.status(Status.BAD_REQUEST_400).send(Map.of("id", id));
                return;
              }
              try {
                res.send(chapterDocument(id));
              } catch (IOException e) {
                res.status(Status.NOT_FOUND_404).send(Map.of("message", e.getMessage(), "kind", e.getClass().getSimpleName()));
              }
            })
            .post("/api/code", (req, res) -> {
              var code = req.content().as(Model.Code.class);
              var execution = executeCode(code);
              res.send(execution);
            })
        )
        .build()
        .start();
  }
}