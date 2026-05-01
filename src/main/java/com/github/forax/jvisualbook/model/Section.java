package com.github.forax.jvisualbook.model;

import java.util.List;
import java.util.Objects;

public record Section(String title, List<Content> contents) {
  public Section {
    Objects.requireNonNull(title);
    contents = List.copyOf(contents);
  }
}