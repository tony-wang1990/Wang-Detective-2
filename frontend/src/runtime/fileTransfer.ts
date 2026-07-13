import { Browser } from '@capacitor/browser';
import { Directory, Filesystem } from '@capacitor/filesystem';
import { Share } from '@capacitor/share';
import { clientKind } from './client';

function safeFileName(value: string) {
  const normalized = value.trim().replace(/[\\/:*?"<>|\r\n]/g, '_');
  return normalized || `wang-detective-${Date.now()}.bin`;
}

function blobToBase64(blob: Blob): Promise<string> {
  return new Promise((resolve, reject) => {
    const reader = new FileReader();
    reader.onerror = () => reject(reader.error || new Error('文件读取失败'));
    reader.onload = () => {
      const value = String(reader.result || '');
      resolve(value.includes(',') ? value.slice(value.indexOf(',') + 1) : value);
    };
    reader.readAsDataURL(blob);
  });
}

export function filenameFromDisposition(disposition: string | undefined, fallback: string) {
  if (!disposition) return safeFileName(fallback);
  const utf8 = disposition.match(/filename\*=UTF-8''([^;]+)/i)?.[1];
  if (utf8) {
    try {
      return safeFileName(decodeURIComponent(utf8));
    } catch {
      return safeFileName(utf8);
    }
  }
  const quoted = disposition.match(/filename="?([^";]+)"?/i)?.[1];
  return safeFileName(quoted || fallback);
}

export async function saveBlob(blob: Blob, requestedName: string) {
  const fileName = safeFileName(requestedName);
  if (clientKind() === 'android') {
    const data = await blobToBase64(blob);
    const saved = await Filesystem.writeFile({
      path: `WangDetective/${fileName}`,
      data,
      directory: Directory.Cache,
      recursive: true
    });
    await Share.share({
      title: fileName,
      text: 'W-探长导出文件',
      url: saved.uri,
      dialogTitle: '保存或分享文件'
    });
    return saved.uri;
  }

  const url = URL.createObjectURL(blob);
  const anchor = document.createElement('a');
  anchor.href = url;
  anchor.download = fileName;
  document.body.appendChild(anchor);
  anchor.click();
  anchor.remove();
  window.setTimeout(() => URL.revokeObjectURL(url), 1000);
  return fileName;
}

export async function openExternalUrl(url: string) {
  if (clientKind() === 'android') {
    await Browser.open({ url });
    return;
  }
  window.open(url, '_blank', 'noopener,noreferrer');
}
