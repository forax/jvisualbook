package com.github.forax.jvisualbook.model;

import java.util.Objects;

public record Text(String text) implements Content {
  public Text {
    Objects.requireNonNull(text);
  }
}