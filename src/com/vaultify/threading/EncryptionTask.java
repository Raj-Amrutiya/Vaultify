package com.vaultify.threading;

import java.util.concurrent.Callable;

import com.vaultify.crypto.AESEngine;

/**
 * Callable task for parallel encryption operations.
 * Used by ThreadManager for concurrent file encryption.
 */
public class EncryptionTask implements Callable<byte[]> {
    private final byte[] plaintext;
    private final byte[] key;
    private final byte[] iv;

    public EncryptionTask(byte[] plaintext, byte[] key, byte[] iv) {
        this.plaintext = plaintext;
        this.key = key;
        this.iv = iv;
    }

    @Override
    public byte[] call() throws Exception {
        return AESEngine.encryptWithParams(plaintext, key, iv);
    }
}
