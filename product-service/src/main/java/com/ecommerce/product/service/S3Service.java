package com.ecommerce.product.service;

import io.awspring.cloud.s3.ObjectMetadata;
import io.awspring.cloud.s3.S3Template;
import io.github.resilience4j.retry.annotation.Retry;
import java.io.IOException;
import java.net.URL;
import java.time.Duration;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/**
 * Manages product image uploads to AWS S3 and generates pre-signed download URLs.
 *
 * <p>Stores the S3 <b>key</b> in the database; pre-signed URLs are generated on read to avoid
 * expiry. Validates file size, MIME type, and filename to prevent path traversal and resource
 * exhaustion.
 */
@Service
public class S3Service {

    private static final Logger log = LoggerFactory.getLogger(S3Service.class);

    private static final long DEFAULT_MAX_FILE_SIZE = 5 * 1024 * 1024; // 5MB
    private static final java.util.Set<String> ALLOWED_CONTENT_TYPES =
            java.util.Set.of("image/jpeg", "image/png", "image/webp", "image/jpg");

    private final S3Template s3Template;
    private final String bucket;
    private final Duration presignedDuration;
    private final long maxFileSize;

    public S3Service(
            S3Template s3Template,
            @Value("${app.s3.bucket}") String bucket,
            @Value("${app.s3.presigned-duration:PT1H}") Duration presignedDuration,
            @Value("${app.s3.max-file-size-mb:5}") long maxFileSizeMb) {
        this.s3Template = s3Template;
        this.bucket = bucket;
        this.presignedDuration = presignedDuration;
        this.maxFileSize = maxFileSizeMb * 1024 * 1024;
    }

    /**
     * Uploads a file to the configured S3 bucket and returns the stored S3 key. The pre-signed URL
     * should be generated via {@link #createPresignedUrl(String)} on read.
     *
     * <p>Transient S3 failures are retried with exponential backoff; validation failures are not
     * retried.
     *
     * @param file the multipart file to upload
     * @return the S3 key (to be persisted as imageUrl/imageKey)
     * @throws IOException if the upload fails
     * @throws IllegalArgumentException if validation fails
     */
    @Retry(name = "s3Upload")
    public String uploadImage(MultipartFile file) throws IOException {
        validateFile(file);

        String sanitized = sanitizeFilename(file.getOriginalFilename());
        String key = UUID.randomUUID() + "-" + sanitized;

        String contentType = file.getContentType();
        ObjectMetadata meta =
                ObjectMetadata.builder()
                        .contentType(contentType)
                        .contentLength(file.getSize())
                        .build();

        try (var inputStream = file.getInputStream()) {
            s3Template.upload(bucket, key, inputStream, meta);
        }

        log.info(
                "image_uploaded bucket={} key={} contentType={} size={}",
                bucket,
                key,
                contentType,
                file.getSize());
        return key;
    }

    /**
     * Generates a time-limited pre-signed GET URL for the given S3 key.
     *
     * @param key the S3 object key
     * @return pre-signed URL string
     */
    public String createPresignedUrl(String key) {
        if (key == null || key.isBlank()) {
            return null;
        }
        // If already a presigned URL (legacy data), return as-is
        if (key.startsWith("http://") || key.startsWith("https://")) {
            return key;
        }
        URL signed = s3Template.createSignedGetURL(bucket, key, presignedDuration);
        return signed.toString();
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("File is empty");
        }
        if (file.getOriginalFilename() == null || file.getOriginalFilename().isBlank()) {
            throw new IllegalArgumentException("File name is missing");
        }
        String filename = file.getOriginalFilename();
        if (filename.contains("..") || filename.contains("/") || filename.contains("\\")) {
            throw new IllegalArgumentException("Invalid file name: " + filename);
        }
        if (file.getSize() > maxFileSize) {
            throw new IllegalArgumentException(
                    "File too large: " + file.getSize() + " bytes, max " + maxFileSize);
        }
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_CONTENT_TYPES.contains(contentType.toLowerCase())) {
            throw new IllegalArgumentException(
                    "Unsupported content type: "
                            + contentType
                            + ". Allowed: "
                            + ALLOWED_CONTENT_TYPES);
        }
        if ("dev-bucket-placeholder".equals(bucket) || "your-bucket-name".equals(bucket)) {
            log.warn(
                    "s3_bucket_placeholder bucket={} — configure S3_BUCKET_NAME for real uploads",
                    bucket);
        }
    }

    private String sanitizeFilename(String original) {
        String cleaned = org.springframework.util.StringUtils.cleanPath(original);
        // Replace any remaining unsafe chars, keep alphanumeric, dot, dash, underscore
        return cleaned.replaceAll("[^a-zA-Z0-9._-]", "_");
    }
}
