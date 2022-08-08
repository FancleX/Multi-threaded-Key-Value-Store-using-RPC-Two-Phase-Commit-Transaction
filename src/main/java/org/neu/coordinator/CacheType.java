package org.neu.coordinator;

/**
 * Cache type indicator to demonstrate the current phase of the transaction
 */
public enum CacheType {
    REQ_PREPARE, ACCEPT, REJECT, ACK_COMMIT, ACK_ABORT
}
