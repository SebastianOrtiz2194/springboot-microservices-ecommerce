package com.ecommerce.product.service;

import io.awspring.cloud.s3.ObjectMetadata;
import io.awspring.cloud.s3.S3Template;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URL;
import java.time.Duration;
import java.util.UUID;

/**
 * Manages product image uploads to AWS S3 and generates pre-signed download URLs.
 */
@Service
public class S3Service {

    private static final Logger log = LoggerFactory.getLogger(S3Service.class);

    private final S3Template s3Template;
    private final String bucket;

    public S3Service(S3Template s3Template,
                     @Value("${app.s3.bucket}") String bucket) {
        this.s3Template = s3Template;
        this.bucket = bucket;
    }

    /**
     * Uploads a file to the configured S3 bucket and returns a pre-signed URL
     * valid for one hour.
     *
     * @param file the multipart file to upload
     * @return a time-limited pre-signed URL for accessing the uploaded file
     * @throws IOException if the upload fails
     */
    public String uploadImage(MultipartFile file) throws IOException {
        String key = UUID.randomUUID() + "-" + file.getOriginalFilename();

        ObjectMetadata meta = ObjectMetadata.builder()
                .contentType(file.getContentType())
                .contentLength(file.getSize())
                .build();

        s3Template.upload(bucket, key, file.getInputStream(), meta);

        URL signed = s3Template.createSignedGetURL(bucket, key, Duration.ofHours(1));
        log.info("image_uploaded bucket={} key={}", bucket, key);
        return signed.toString();
    }
}
