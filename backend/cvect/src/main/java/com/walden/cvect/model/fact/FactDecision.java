package com.walden.cvect.model.fact;

public final class FactDecision {

    public enum Type {
        ACCEPT,
        REJECT,
        ABSTAIN
    }

    private final Type type;
    private final String reason;

    private FactDecision(Type type, String reason) {
        this.type = type;
        this.reason = reason;
    }

    // ---------- factory methods ----------

    public static FactDecision accept(String reason) {
        return new FactDecision(Type.ACCEPT, reason);
    }

    public static FactDecision reject(String reason) {
        return new FactDecision(Type.REJECT, reason);
    }

    public static FactDecision abstain() {
        return new FactDecision(Type.ABSTAIN, null);
    }

    // ---------- helpers ----------

    public boolean isAccepted() {
        return type == Type.ACCEPT;
    }

    public boolean isRejected() {
        return type == Type.REJECT;
    }

    public boolean isAbstain() {
        return type == Type.ABSTAIN;
    }

    public String getReason() {
        return reason;
    }

    public Type getType() {
        return type;
    }
}