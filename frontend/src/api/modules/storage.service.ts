import { api } from './api';

type PresignedUrl = {
  uploadUrl: string;
  downloadUrl: string;
  key: string;
};

export const StorageService = {
  getPresignedUpload(filename: string, contentType: string) {
    return api<PresignedUrl>(
      `/storage/presigned-url?filename=${encodeURIComponent(filename)}&contentType=${encodeURIComponent(contentType)}`,
    );
  },
  // The presigned URL already carries its own signature — it must be hit
  // directly, never through api() (which would attach our JWT and confuse MinIO).
  async upload(uploadUrl: string, blob: Blob, contentType: string) {
    const response = await fetch(uploadUrl, {
      method: 'PUT',
      body: blob,
      headers: { 'Content-Type': contentType },
    });
    if (!response.ok) {
      throw new Error(`Falha ao enviar arquivo (status ${response.status})`);
    }
  },
};
