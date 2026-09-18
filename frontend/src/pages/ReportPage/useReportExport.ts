import { useCallback, useState } from 'react';
import { StorageService } from '../../api/modules/storage.service';

export function useReportExport() {
  const [exporting, setExporting] = useState(false);
  const [exportedUrl, setExportedUrl] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  const exportAsPng = useCallback(
    async (element: HTMLElement, filenamePrefix: string, backgroundColor: string) => {
      setExporting(true);
      setError(null);
      setExportedUrl(null);
      try {
        const { default: html2canvas } = await import('html2canvas');
        const canvas = await html2canvas(element, { backgroundColor });
        const blob = await new Promise<Blob>((resolve, reject) => {
          canvas.toBlob(
            (result) => (result ? resolve(result) : reject(new Error('Falha ao gerar imagem'))),
            'image/png',
          );
        });
        const filename = `${filenamePrefix}-${Date.now()}.png`;
        const presigned = await StorageService.getPresignedUpload(filename, 'image/png');
        await StorageService.upload(presigned.uploadUrl, blob, 'image/png');
        setExportedUrl(presigned.downloadUrl);
      } catch (err) {
        setError(err instanceof Error ? err.message : 'Falha ao exportar relatório');
      } finally {
        setExporting(false);
      }
    },
    [],
  );

  return { exportAsPng, exporting, exportedUrl, error };
}
