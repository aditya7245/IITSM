package com.iitsm.domain.incident;

/** Kind of entry on an incident's comment thread. */
public enum CommentType {
    /** Internal investigation note, visible to staff only. */
    NOTE,
    /** Technician asking the reporter for more information. */
    INFO_REQUEST,
    /** Reporter answering an information request. */
    INFO_RESPONSE
}
