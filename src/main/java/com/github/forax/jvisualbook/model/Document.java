package com.github.forax.jvisualbook.model;

import java.util.List;

public record Document(List<Section> sections) {
  public Document {
    sections = List.copyOf(sections);
  }
}