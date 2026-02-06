package com.vaultify.service;

import java.nio.file.Path;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.List;

import com.vaultify.crypto.HashUtil;
import com.vaultify.models.CredentialMetadata;
import com.vaultify.repository.CredentialRepository;
import com.vaultify.repository.RepositoryFactory;
import com.vaultify.threading.ThreadManager;
import com.vaultify.util.CredentialFileManager;

public class VaultService {
    private final LedgerService ledgerService;
    private final CredentialRepository credentialRepository;

    public VaultService() {
        this.ledgerService = new LedgerService();
        this.credentialRepository = RepositoryFactory.get().credentialRepository();
    }

    public VaultService(LedgerService ledgerService, CredentialRepository credentialRepository) {
        this.ledgerService = ledgerService;
        this.credentialRepository = credentialRepository;
    }

    public String addCredential(long userId, Path filePath, PublicKey userPublicKey) throws Exception {
        if (filePath == null || !filePath.toFile().exists()) {
            throw new ServiceException("File does not exist: " + filePath);
        }
        CredentialMetadata meta = CredentialFileManager.encryptAndStore(filePath, userPublicKey, userId);
        credentialRepository.save(meta, userId);
        String dataHash = HashUtil.sha256(meta.credentialIdString + ":" + meta.dataHash);
        ThreadManager.runAsync(() -> ledgerService.appendBlock(userId, "user_" + userId, "ADD_CREDENTIAL", dataHash));
        return meta.credentialIdString;
    }

    public List<CredentialMetadata> listCredentials(long userId) {
        return credentialRepository.findByUserId(userId);
    }

    public byte[] retrieveCredential(String credentialId, PrivateKey userPrivateKey) throws Exception {
        if (credentialId == null || credentialId.isEmpty()) {
            throw new ServiceException("Credential ID cannot be empty");
        }
        CredentialMetadata meta = credentialRepository.findByCredentialId(credentialId);
        if (meta == null) {
            throw new ServiceException("Credential not found: " + credentialId);
        }
        return CredentialFileManager.decryptAndRetrieve(
                credentialId,
                meta.encryptedKeyBase64,
                meta.ivBase64,
                userPrivateKey);
    }

    public void deleteCredential(String credentialId, long userId) throws Exception {
        if (credentialId == null || credentialId.isEmpty()) {
            throw new ServiceException("Credential ID cannot be empty");
        }
        CredentialMetadata meta = credentialRepository.findByCredentialId(credentialId);
        if (meta == null) {
            throw new ServiceException("Credential not found: " + credentialId);
        }
        if (meta.userId != userId) {
            throw new SecurityException("Unauthorized: credential belongs to different user");
        }
        CredentialFileManager.deleteEncryptedFile(credentialId);
        credentialRepository.deleteByCredentialId(credentialId);
        String dataHash = HashUtil.sha256("DELETE:" + credentialId);
        ThreadManager
                .runAsync(() -> ledgerService.appendBlock(userId, "user_" + userId, "DELETE_CREDENTIAL", dataHash));
    }
}
