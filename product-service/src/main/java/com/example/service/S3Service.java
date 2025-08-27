package com.example.service;

import com.example.model.Product;
import com.example.repository.ProductRepository;
import io.awspring.cloud.s3.ObjectMetadata;
import io.awspring.cloud.s3.S3Template;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.CacheConfig;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.IOException;
import java.net.URL;
import java.time.Duration;
import java.util.UUID;

@Service
@CacheConfig(cacheNames = "products")
public class S3Service {

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private S3Template s3Template;

    @Value("${app.s3.bucket}")   // put your bucket name in application.yml as app.s3.bucket
    private String bucket;

    @Cacheable(key = "#id")
    public Product getProduct(Long id) {
        return productRepository.findById(id).orElseThrow();
    }

    @CachePut(key = "#result.id")
    public Product createProduct(Product product) {
        return productRepository.save(product);
    }

    public String uploadImage(MultipartFile file) throws IOException {
        String key = UUID.randomUUID() + "-" + file.getOriginalFilename();

        ObjectMetadata meta = ObjectMetadata.builder()
                .contentType(file.getContentType())
                .contentLength(file.getSize())
                .build();

        // Upload
        s3Template.upload(bucket, key, file.getInputStream(), meta);

        // Return a time-limited URL (safer than making the object public)
        URL signed = s3Template.createSignedGetURL(bucket, key, Duration.ofHours(1));
        return signed.toString();
    }
}