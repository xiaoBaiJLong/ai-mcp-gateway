package com.xiaobaijlong.mcpgateway.management.authorization;

import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Optional;

@Service
public class AgentApiKeyService {

    static final int API_KEY_PREFIX_LENGTH = 12;
    private static final int RANDOM_BYTE_LENGTH = 32;
    private static final byte[] DUMMY_DIGEST = new byte[32];

    private final SecureRandom secureRandom = new SecureRandom();
    private final AuthorizationRepository repository;

    public AgentApiKeyService(AuthorizationRepository repository) {
        this.repository = repository;
    }

    KeyMaterial generate() {
        byte[] randomBytes = new byte[RANDOM_BYTE_LENGTH];
        secureRandom.nextBytes(randomBytes);
        String plainText = "mgw_" + Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
        return new KeyMaterial(
                plainText,
                plainText.substring(0, API_KEY_PREFIX_LENGTH),
                digest(plainText)
        );
    }

    public Optional<AuthenticatedAgent> authenticate(String apiKey) {
        String candidateKey = apiKey == null ? "" : apiKey;
        byte[] candidateDigest = digest(candidateKey);
        String prefix = candidateKey.length() >= API_KEY_PREFIX_LENGTH
                ? candidateKey.substring(0, API_KEY_PREFIX_LENGTH)
                : "";

        var candidates = repository.findAgentsByApiKeyPrefix(prefix);
        AuthenticatedAgent authenticated = null;
        if (candidates.isEmpty()) {
            MessageDigest.isEqual(candidateDigest, DUMMY_DIGEST);
        }
        for (AuthorizationRepository.AgentRow candidate : candidates) {
            // 摘要比较必须恒定时间，不能用数组或字符串的提前退出比较。
            if (MessageDigest.isEqual(candidateDigest, candidate.apiKeyDigest())) {
                authenticated = new AuthenticatedAgent(candidate.id(), candidate.name());
            }
        }
        return Optional.ofNullable(authenticated);
    }

    private byte[] digest(String apiKey) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(apiKey.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("运行环境不支持 SHA-256", exception);
        }
    }

    record KeyMaterial(String plainText, String prefix, byte[] digest) {
    }

    public record AuthenticatedAgent(long id, String name) {
    }
}
