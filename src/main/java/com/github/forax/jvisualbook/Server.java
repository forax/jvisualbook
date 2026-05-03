package com.github.forax.jvisualbook;

import io.helidon.http.media.MediaContext;
import io.helidon.http.media.jackson.JacksonSupport;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.staticcontent.StaticContentFeature;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public final class Server {
  record Chapter(String name) {}

  private static String removeExtension(String filename) {
    return filename.substring(0, filename.lastIndexOf('.'));
  }

  private static List<Chapter> allChapters() throws IOException {
    try(var files = Files.list(Path.of("."))) {
      return files
          .map(f -> f.getFileName().toString())
          .filter(n -> n.endsWith(".jsh"))
          .map(Server::removeExtension)
          .map(Chapter::new)
          .toList();
    }
  }

  private static Model.Document chapterDocument(String name) throws IOException {
    var path = Path.of(".", name + ".jsh");
    return DocumentParser.parse(path);
  }

  static void main() {
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
            .get("/api/chapter", (_, res) ->
                res.send(allChapters())
            )
            .get("/api/chapter/{id}", (req, res) -> {
              var id = req.path().pathParameters().get("id");
              res.send(chapterDocument(id));
            })
        )
        .build()
        .start();
  }
}