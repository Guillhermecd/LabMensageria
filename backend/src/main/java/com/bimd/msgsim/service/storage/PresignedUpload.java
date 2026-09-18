package com.bimd.msgsim.service.storage;

public record PresignedUpload(String uploadUrl, String downloadUrl, String key) {
}
