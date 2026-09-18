package com.bimd.msgsim.service.storage;

import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

/**
 * Generic presigned-URL storage, reused as-is from the template contract (profile picture
 * upload in the original design). Fase 7 is the first caller other than auth/profile: it
 * uploads exported report snapshots under the same bucket, just a different key prefix.
 */
@Service
@RequiredArgsConstructor
public class StorageService {

    private final S3Presigner presigner;

    @Value("${app.storage.bucket}")
    private String bucket;

    @Value("${app.storage.presigned-url-expires-in}")
    private long expiresInSeconds;

    public PresignedUpload createPresignedUpload(String key, String contentType) {
        PutObjectRequest putRequest = PutObjectRequest.builder()
                .bucket(bucket).key(key).contentType(contentType).build();
        String uploadUrl = presigner.presignPutObject(PutObjectPresignRequest.builder()
                        .signatureDuration(Duration.ofSeconds(expiresInSeconds))
                        .putObjectRequest(putRequest)
                        .build())
                .url()
                .toString();

        GetObjectRequest getRequest = GetObjectRequest.builder().bucket(bucket).key(key).build();
        String downloadUrl = presigner.presignGetObject(GetObjectPresignRequest.builder()
                        .signatureDuration(Duration.ofSeconds(expiresInSeconds))
                        .getObjectRequest(getRequest)
                        .build())
                .url()
                .toString();

        return new PresignedUpload(uploadUrl, downloadUrl, key);
    }
}
