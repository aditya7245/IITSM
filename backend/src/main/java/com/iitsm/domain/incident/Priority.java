package com.iitsm.domain.incident;

/** Incident priority. P1 is the most urgent. */
public enum Priority {
    P1,
    P2,
    P3,
    P4;

    /** The next more urgent tier (P4 -> P3 -> P2 -> P1). P1 stays P1. */
    public Priority bump() {
        return this == P1 ? P1 : values()[ordinal() - 1];
    }
}
