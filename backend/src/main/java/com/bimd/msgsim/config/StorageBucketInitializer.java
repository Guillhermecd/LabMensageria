package com.bimd.msgsim.config;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;

/** Creates the bucket on boot so a fresh MinIO instance works without a manual setup step. */
@Component
@RequiredArgsConstructor
public class StorageBucketInitializer implements CommandLineRunner {

    private final S3Client s3Client;

    @Value("${app.storage.bucket}")
    private String bucket;

    @Override
    public void run(String... args) {
        try {
            s3Client.headBucket(HeadBucketRequest.builder().bucket(bucket).build());
        } catch (NoSuchBucketException e) {
            s3Client.createBucket(CreateBucketRequest.builder().bucket(bucket).build());
        }
    }
}
