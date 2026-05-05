package com.github.forax.jvisualbook;

import java.util.List;
import java.util.Objects;

public interface Model {
  record Document(List<Section> sections) {
    public Document {
      sections = List.copyOf(sections);
    }
  }
  record Section(String title, List<Content> contents) {
    public Section {
      Objects.requireNonNull(title);
      contents = List.copyOf(contents);
    }
  }
  record Content(Kind kind, String text) {
    public enum Kind {
      TEXT,
      CODE,
      OUTPUT
    }

    public Content {
      Objects.requireNonNull(kind);
      Objects.requireNonNull(text);
    }
  }

  record Code(List<Snippet> snippets) {
    public Code {
      snippets = List.copyOf(snippets);
    }
  }
  record Snippet(String code) {
    public Snippet {
      Objects.requireNonNull(code);
    }
  }

  record Execution(List<Evaluation> evaluations) {
    public Execution {
      evaluations = List.copyOf(evaluations);
    }
  }
  record Evaluation(Status status, String text) {
    public enum Status {
      ERROR, SUCCESS
    }

    public Evaluation {
      Objects.requireNonNull(status);
      Objects.requireNonNull(text);
    }
  }
}
