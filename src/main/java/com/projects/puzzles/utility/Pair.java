package com.projects.puzzles.utility;
public record Pair<F, S>(
        F first,
        S second
) {
    public static <F, S> Pair<F, S> of(F first, S second) {
        return new Pair<>(first, second);
    }

    public F getFirst() {
        return first;
    }

    public S getSecond() {
        return second;
    }

    public Pair<S, F> swap() {
        return new Pair<>(second, first);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Pair<?, ?> pair)) return false;

        if (!first.equals(pair.first)) return false;
        return second.equals(pair.second);
    }

    @Override
    public int hashCode() {
        int result = first.hashCode();
        result = 31 * result + second.hashCode();
        return result;
    }

    public F getKey() {
        return first;
    }

    public S getValue() {
        return second;
    }
}
