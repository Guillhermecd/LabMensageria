package com.bimd.msgsim.domain.dto;

public record PresignedUrlResponse(String uploadUrl, String downloadUrl, String key) {
}
