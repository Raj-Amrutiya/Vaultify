package com.vaultify.crypto;

import java.security.Key;

public interface CryptoEngine {

    /**
     * Encrypt data using the provided key.
     * 
     * @param data Plaintext data
     * @param key  Encryption key (SecretKey for AES, PublicKey for RSA)
     * @return Encrypted data (ciphertext)
     */
    byte[] encrypt(byte[] data, Key key) throws Exception;

    /**
     * Decrypt data using the provided key.
     * 
     * @param data Ciphertext data
     * @param key  Decryption key (SecretKey for AES, PrivateKey for RSA)
     * @return Decrypted data (plaintext)
     */
    byte[] decrypt(byte[] data, Key key) throws Exception;
}
