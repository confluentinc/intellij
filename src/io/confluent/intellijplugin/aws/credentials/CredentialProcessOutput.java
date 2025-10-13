package io.confluent.intellijplugin.aws.credentials;

import java.time.Instant;

@SuppressWarnings("unused")
public class CredentialProcessOutput {
    private String accessKeyId = "";
    private String secretAccessKey = "";
    private String sessionToken = null;
    private Instant expiration = null;

    public CredentialProcessOutput() {
    }

    public CredentialProcessOutput(String accessKeyId, String secretAccessKey, String sessionToken, Instant expiration) {
        this.accessKeyId = accessKeyId;
        this.secretAccessKey = secretAccessKey;
        this.sessionToken = sessionToken;
        this.expiration = expiration;
    }

    public String getAccessKeyId() {
        return accessKeyId;
    }

    public void setAccessKeyId(String accessKeyId) {
        this.accessKeyId = accessKeyId;
    }

    public String getSecretAccessKey() {
        return secretAccessKey;
    }

    public void setSecretAccessKey(String secretAccessKey) {
        this.secretAccessKey = secretAccessKey;
    }

    public String getSessionToken() {
        return sessionToken;
    }

    public void setSessionToken(String sessionToken) {
        this.sessionToken = sessionToken;
    }

    public Instant getExpiration() {
        return expiration;
    }

    public void setExpiration(Instant expiration) {
        this.expiration = expiration;
    }
}
