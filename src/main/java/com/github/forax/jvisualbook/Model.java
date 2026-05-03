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

  record Content(Kind kind, String text){
    enum Kind {
      TEXT,
      CODE,
      OUTPUT
    }
  }
}
