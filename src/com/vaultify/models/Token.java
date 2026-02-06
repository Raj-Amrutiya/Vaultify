package com.vaultify.models;

import java.sql.Timestamp;

public class Token {
    private long id;
    private long credentialId;
    private long issuerUserId;
    private String token;
    private Timestamp expiry;
    private boolean revoked;
    private Timestamp createdAt;

    // Getters and Setters

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public long getCredentialId() {
        return credentialId;
    }

    public void setCredentialId(long credentialId) {
        this.credentialId = credentialId;
    }

    public long getIssuerUserId() {
        return issuerUserId;
    }

    public void setIssuerUserId(long issuerUserId) {
        this.issuerUserId = issuerUserId;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public Timestamp getExpiry() {
        return expiry;
    }

    public void setExpiry(Timestamp expiry) {
        this.expiry = expiry;
    }

    public boolean isRevoked() {
        return revoked;
    }

    public void setRevoked(boolean revoked) {
        this.revoked = revoked;
    }

    public Timestamp getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Timestamp createdAt) {
        this.createdAt = createdAt;
    }

    public boolean isExpired() {
        return expiry != null && expiry.before(new Timestamp(System.currentTimeMillis()));
    }

    public boolean isValid() {
        return !revoked && !isExpired();
    }
}