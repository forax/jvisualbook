package com.github.forax.jvisualbook;

import com.github.forax.jvisualbook.model.Document;
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

  private static Document chapterDocument(String name) throws IOException {
    var path = Path.of(".", name + ".jsh");
    return DocumentParser.parse(path);
  }

  static void main() {
    WebServer.builder()
        .port(8080)
        .host("localhost")
        .addFeature(StaticContentFeature.builder()
            //.addPath(p -> p.location(Path.of("public"))
            //    .welcome("index.html")
            //    .context("/"))
            .addClasspath(cl -> cl.location("/public")
                .welcome("index.html")
                .context("/"))
            .build()
        )
        .routing(it -> it
            .get("/api/chapter", (req, res) -> {
              res.send(allChapters());
            })
            .get("/api/chapter/{id}", (req, res) -> {
              var id = req.path().pathParameters().get("id");
              res.send(chapterDocument(id));
            })
        )
        .build()
        .start();
  }
}
