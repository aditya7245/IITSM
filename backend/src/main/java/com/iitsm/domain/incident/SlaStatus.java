package com.iitsm.domain.incident;

/** SLA health of an incident. Declared in increasing order of severity. */
public enum SlaStatus {
    ON_TRACK,
    AT_RISK,
    VIOLATED;

    public boolean isMoreSevereThan(SlaStatus other) {
        return this.compareTo(other) > 0;
    }
}
