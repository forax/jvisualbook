package com.github.forax.jvisualbook;

import jdk.jshell.Snippet;

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
  record Content(Kind kind, String text){
    enum Kind {
      TEXT,
      CODE,
      OUTPUT
    }
  }

  record Code(List<Snippet> snippets) {
    public Code {
      snippets = List.copyOf(snippets);
    }
  }
  record Snippet(int id, String code) {
    public Snippet {
      Objects.requireNonNull(code);
    }
  }

  record Execution(List<Evaluation> evaluations) {
    public Execution {
      evaluations = List.copyOf(evaluations);
    }
  }
  record Evaluation(int id, String text) {
    public Evaluation {
      Objects.requireNonNull(text);
    }
  }
}
