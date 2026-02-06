package com.vaultify.crypto;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.*;
import java.security.spec.*;
import java.util.Base64;

public class KeyManager {

    public KeyPair getOrCreateUserKeyPair(String userId, Path publicPath, Path privatePath, int keySize) throws Exception {
        if (Files.exists(publicPath) && Files.exists(privatePath)) {
            PublicKey pub = loadPublicKey(publicPath);
            PrivateKey priv = loadPrivateKey(privatePath);
            return new KeyPair(pub, priv);
        } else {
            KeyPair kp = RSAEngine.generateKeyPair(keySize);
            saveKeyPair(kp, publicPath, privatePath);
            return kp;
        }
    }

    public void saveKeyPair(KeyPair keyPair, Path publicKeyPath, Path privateKeyPath) throws IOException {
        // Public key (X.509)
        byte[] pubEncoded = keyPair.getPublic().getEncoded();
        String pubPem = encodePem(pubEncoded, "PUBLIC KEY");
        Files.createDirectories(publicKeyPath.getParent());
        Files.write(publicKeyPath, pubPem.getBytes());

        // Private key (PKCS#8)
        byte[] privEncoded = keyPair.getPrivate().getEncoded();
        String privPem = encodePem(privEncoded, "PRIVATE KEY");
        Files.createDirectories(privateKeyPath.getParent());
        Files.write(privateKeyPath, privPem.getBytes());
    }

    public PublicKey loadPublicKey(Path path) throws Exception {
        byte[] pem = Files.readAllBytes(path);
        String s = new String(pem).replaceAll("-----BEGIN PUBLIC KEY-----", "")
                .replaceAll("-----END PUBLIC KEY-----", "")
                .replaceAll("\\s+", "");
        byte[] decoded = Base64.getDecoder().decode(s);
        X509EncodedKeySpec spec = new X509EncodedKeySpec(decoded);
        KeyFactory kf = KeyFactory.getInstance("RSA");
        return kf.generatePublic(spec);
    }

    public PrivateKey loadPrivateKey(Path path) throws Exception {
        byte[] pem = Files.readAllBytes(path);
        String s = new String(pem).replaceAll("-----BEGIN PRIVATE KEY-----", "")
                .replaceAll("-----END PRIVATE KEY-----", "")
                .replaceAll("\\s+", "");
        byte[] decoded = Base64.getDecoder().decode(s);
        PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(decoded);
        KeyFactory kf = KeyFactory.getInstance("RSA");
        return kf.generatePrivate(spec);
    }

    private String encodePem(byte[] der, String header) {
        String b64 = Base64.getEncoder().encodeToString(der);
        StringBuilder sb = new StringBuilder();
        sb.append("-----BEGIN ").append(header).append("-----\n");
        int idx = 0;
        while (idx < b64.length()) {
            int end = Math.min(idx + 64, b64.length());
            sb.append(b64, idx, end).append("\n");
            idx = end;
        }
        sb.append("-----END ").append(header).append("-----\n");
        return sb.toString();
    }
}
