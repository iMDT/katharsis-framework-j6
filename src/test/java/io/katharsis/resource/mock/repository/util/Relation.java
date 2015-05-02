package io.katharsis.resource.mock.repository.util;

import java.io.Serializable;
import java.util.Objects;

public class Relation<T> {
    private T source;
    private Serializable targetId;
    private String fieldName;

    public Relation(T source, Serializable targetId, String fieldName) {
        this.source = source;
        this.targetId = targetId;
        this.fieldName = fieldName;
    }

    public T getSource() {
        return source;
    }

    public Serializable getTargetId() {
        return targetId;
    }

    public String getFieldName() {
        return fieldName;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Relation)) return false;
        Relation<?> relation = (Relation<?>) o;
        return Objects.equals(source, relation.source) &&
                Objects.equals(targetId, relation.targetId) &&
                Objects.equals(fieldName, relation.fieldName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(source, targetId, fieldName);
    }
}
