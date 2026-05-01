package com.github.forax.jvisualbook.model;

import java.util.Objects;

public record Code(String code) implements Content {
  public Code {
    Objects.requireNonNull(code);
  }
}