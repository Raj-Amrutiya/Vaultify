package com.vaultify.models;

/**
 * Ledger block data model (matches server response structure)
 */
public class LedgerBlock {
    private int index;
    private long timestamp;
    private String action;
    private String dataHash;
    private String prevHash;
    private String hash;

    public LedgerBlock() {
    }

    public LedgerBlock(int index, long timestamp, String action, String dataHash, String prevHash, String hash) {
        this.index = index;
        this.timestamp = timestamp;
        this.action = action;
        this.dataHash = dataHash;
        this.prevHash = prevHash;
        this.hash = hash;
    }

    public int getIndex() {
        return index;
    }

    public void setIndex(int index) {
        this.index = index;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public String getDataHash() {
        return dataHash;
    }

    public void setDataHash(String dataHash) {
        this.dataHash = dataHash;
    }

    public String getPrevHash() {
        return prevHash;
    }

    public void setPrevHash(String prevHash) {
        this.prevHash = prevHash;
    }

    public String getHash() {
        return hash;
    }

    public void setHash(String hash) {
        this.hash = hash;
    }
}
