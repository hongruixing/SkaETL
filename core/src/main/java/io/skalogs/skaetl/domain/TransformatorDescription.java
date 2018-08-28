package io.skalogs.skaetl.domain;


import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public class TransformatorDescription {
    private final String name;
    private final String description;
}
