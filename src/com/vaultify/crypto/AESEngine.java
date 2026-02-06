package com.vaultify.crypto;

import java.security.Key;
import java.security.SecureRandom;
import java.util.Arrays;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public class AESEngine implements CryptoEngine {
    public static final int AES_256_KEY_BYTES = 32; // 256-bit
    public static final int GCM_IV_BYTES = 12; // 96-bit IV for GCM
    public static final int GCM_TAG_BITS = 128; // 16 bytes tag

    private static final SecureRandom RNG = new SecureRandom();

    @Override
    public byte[] encrypt(byte[] data, Key key) throws Exception {
        if (!(key instanceof SecretKey)) {
            throw new IllegalArgumentException("AES encryption requires a SecretKey");
        }
        byte[] iv = generateIv();
        byte[] cipher = encryptWithParams(data, key.getEncoded(), iv);

        // Prepend IV to ciphertext so caller can store both (IV || Ciphertext)
        byte[] out = new byte[iv.length + cipher.length];
        System.arraycopy(iv, 0, out, 0, iv.length);
        System.arraycopy(cipher, 0, out, iv.length, cipher.length);
        return out;
    }

    @Override
    public byte[] decrypt(byte[] data, Key key) throws Exception {
        if (!(key instanceof SecretKey)) {
            throw new IllegalArgumentException("AES decryption requires a SecretKey");
        }
        if (data == null || data.length < GCM_IV_BYTES + 1) {
            throw new IllegalArgumentException("Invalid ciphertext format");
        }
        // Extract IV from start
        byte[] iv = new byte[GCM_IV_BYTES];
        System.arraycopy(data, 0, iv, 0, GCM_IV_BYTES);
        byte[] cipher = Arrays.copyOfRange(data, GCM_IV_BYTES, data.length);

        return decryptWithParams(cipher, key.getEncoded(), iv);
    }

    // Utilities

    public static byte[] generateKey() {
        byte[] key = new byte[AES_256_KEY_BYTES];
        RNG.nextBytes(key);
        return key;
    }

    public static byte[] generateIv() {
        byte[] iv = new byte[GCM_IV_BYTES];
        RNG.nextBytes(iv);
        return iv;
    }

    public static byte[] encryptWithParams(byte[] plaintext, byte[] key, byte[] iv) throws Exception {
        if (plaintext == null)
            plaintext = new byte[0];
        SecretKey secretKey = new SecretKeySpec(key, "AES");
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_BITS, iv);
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, spec);
        return cipher.doFinal(plaintext);
    }

    public static byte[] decryptWithParams(byte[] ciphertext, byte[] key, byte[] iv) throws Exception {
        SecretKey secretKey = new SecretKeySpec(key, "AES");
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_BITS, iv);
        cipher.init(Cipher.DECRYPT_MODE, secretKey, spec);
        return cipher.doFinal(ciphertext);
    }
}
