package com.walden.cvect.model.fact;

@FunctionalInterface
public interface ChunkFactRule {

    FactDecision apply(ChunkFactContext context);

    ChunkFactRule ALWAYS_TRUE = ctx -> FactDecision.accept("always true");

    ChunkFactRule ALWAYS_FALSE = ctx -> FactDecision.reject("always false");
}
