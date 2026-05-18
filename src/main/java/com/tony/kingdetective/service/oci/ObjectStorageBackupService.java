package com.tony.kingdetective.service.oci;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import com.oracle.bmc.objectstorage.ObjectStorageClient;
import com.oracle.bmc.objectstorage.model.BucketSummary;
import com.oracle.bmc.objectstorage.model.ObjectSummary;
import com.oracle.bmc.objectstorage.requests.DeleteObjectRequest;
import com.oracle.bmc.objectstorage.requests.GetNamespaceRequest;
import com.oracle.bmc.objectstorage.requests.ListBucketsRequest;
import com.oracle.bmc.objectstorage.requests.ListObjectsRequest;
import com.oracle.bmc.objectstorage.requests.PutObjectRequest;
import com.tony.kingdetective.bean.dto.SysUserDTO;
import com.tony.kingdetective.bean.params.oci.objectstorage.ObjectStorageBackupParams;
import com.tony.kingdetective.bean.params.oci.objectstorage.ObjectStorageListObjectsParams;
import com.tony.kingdetective.bean.params.oci.objectstorage.ObjectStorageObjectParams;
import com.tony.kingdetective.bean.response.oci.objectstorage.ObjectStorageBackupRsp;
import com.tony.kingdetective.config.OracleInstanceFetcher;
import com.tony.kingdetective.exception.OciException;
import com.tony.kingdetective.service.ISysService;
import lombok.extern.slf4j.Slf4j;
import net.lingala.zip4j.ZipFile;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@Slf4j
@Service
public class ObjectStorageBackupService {

    private static final DateTimeFormatter BACKUP_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private final ISysService sysService;

    @Value("${king-detective.app-dir:${KING_DETECTIVE_APP_DIR:/app/king-detective}}")
    private String appDir;

    public ObjectStorageBackupService(ISysService sysService) {
        this.sysService = sysService;
    }

    public List<ObjectStorageBackupRsp.BucketInfo> listBuckets(String ociCfgId) {
        try (OracleInstanceFetcher fetcher = createFetcher(ociCfgId)) {
            ObjectStorageClient client = createClient(fetcher);
            try {
            String namespace = getNamespace(client);
            List<BucketSummary> buckets = client.listBuckets(ListBucketsRequest.builder()
                    .namespaceName(namespace)
                    .compartmentId(fetcher.getCompartmentId())
                    .build()).getItems();

            List<ObjectStorageBackupRsp.BucketInfo> result = new ArrayList<>();
            if (buckets != null) {
                for (BucketSummary bucket : buckets) {
                    result.add(ObjectStorageBackupRsp.BucketInfo.builder()
                            .name(bucket.getName())
                            .namespaceName(StrUtil.blankToDefault(bucket.getNamespace(), namespace))
                            .compartmentId(bucket.getCompartmentId())
                            .storageTier("Standard")
                            .timeCreated(toLocalDateTime(bucket.getTimeCreated()))
                            .build());
                }
            }
            return result;
            } finally {
                client.close();
            }
        } catch (Exception e) {
            log.error("Object Storage bucket list failed", e);
            throw new OciException(-1, "读取对象存储桶失败: " + e.getMessage());
        }
    }

    public ObjectStorageBackupRsp.ObjectList listObjects(ObjectStorageListObjectsParams params) {
        try (OracleInstanceFetcher fetcher = createFetcher(params.getOciCfgId())) {
            ObjectStorageClient client = createClient(fetcher);
            try {
            String namespace = getNamespace(client);
            ListObjectsRequest request = ListObjectsRequest.builder()
                    .namespaceName(namespace)
                    .bucketName(params.getBucketName())
                    .prefix(StrUtil.blankToDefault(params.getPrefix(), "wang-detective/backups"))
                    .limit(limit(params.getLimit()))
                    .build();
            List<ObjectSummary> objects = client.listObjects(request).getListObjects().getObjects();
            List<ObjectStorageBackupRsp.ObjectInfo> objectInfos = new ArrayList<>();
            if (objects != null) {
                for (ObjectSummary object : objects) {
                    objectInfos.add(ObjectStorageBackupRsp.ObjectInfo.builder()
                            .name(object.getName())
                            .sizeBytes(object.getSize())
                            .md5(object.getMd5())
                            .timeCreated(toLocalDateTime(object.getTimeCreated()))
                            .build());
                }
            }
            return ObjectStorageBackupRsp.ObjectList.builder()
                    .namespaceName(namespace)
                    .bucketName(params.getBucketName())
                    .prefix(params.getPrefix())
                    .objects(objectInfos)
                    .build();
            } finally {
                client.close();
            }
        } catch (Exception e) {
            log.error("Object Storage object list failed", e);
            throw new OciException(-1, "读取对象存储文件失败: " + e.getMessage());
        }
    }

    public ObjectStorageBackupRsp createBackup(ObjectStorageBackupParams params) {
        File backupFile = createLocalArchive(Boolean.TRUE.equals(params.getIncludeLogs()));
        String namespace = null;
        String objectName = null;

        if (Boolean.TRUE.equals(params.getUploadToObjectStorage())) {
            if (StrUtil.isBlank(params.getBucketName())) {
                throw new OciException(-1, "上传对象存储时必须选择 Bucket");
            }
            try (OracleInstanceFetcher fetcher = createFetcher(params.getOciCfgId());
                 InputStream inputStream = new FileInputStream(backupFile)) {
                ObjectStorageClient client = createClient(fetcher);
                try {
                namespace = getNamespace(client);
                objectName = normalizeObjectName(params.getPrefix(), backupFile.getName());
                client.putObject(PutObjectRequest.builder()
                        .namespaceName(namespace)
                        .bucketName(params.getBucketName())
                        .objectName(objectName)
                        .contentLength(backupFile.length())
                        .putObjectBody(inputStream)
                        .build());
                } finally {
                    client.close();
                }
            } catch (Exception e) {
                log.error("Object Storage backup upload failed", e);
                throw new OciException(-1, "备份上传对象存储失败: " + e.getMessage());
            }
        }

        return ObjectStorageBackupRsp.builder()
                .localPath(backupFile.getAbsolutePath())
                .namespaceName(namespace)
                .bucketName(params.getBucketName())
                .objectName(objectName)
                .sizeBytes(backupFile.length())
                .md5(md5Hex(backupFile))
                .createTime(LocalDateTime.now())
                .build();
    }

    public void deleteObject(ObjectStorageObjectParams params) {
        try (OracleInstanceFetcher fetcher = createFetcher(params.getOciCfgId())) {
            ObjectStorageClient client = createClient(fetcher);
            try {
            String namespace = getNamespace(client);
            client.deleteObject(DeleteObjectRequest.builder()
                    .namespaceName(namespace)
                    .bucketName(params.getBucketName())
                    .objectName(params.getObjectName())
                    .build());
            } finally {
                client.close();
            }
        } catch (Exception e) {
            log.error("Object Storage object delete failed", e);
            throw new OciException(-1, "删除对象存储文件失败: " + e.getMessage());
        }
    }

    public List<String> listLocalBackups(int limit) {
        File dir = FileUtil.mkdir(new File(appDir, "backups").getAbsolutePath());
        File[] files = dir.listFiles((file) -> file.isFile() && file.getName().endsWith(".zip"));
        List<String> result = new ArrayList<>();
        if (files == null) {
            return result;
        }
        java.util.Arrays.stream(files)
                .sorted((a, b) -> Long.compare(b.lastModified(), a.lastModified()))
                .limit(Math.max(1, Math.min(limit, 20)))
                .forEach(file -> result.add(file.getName() + " (" + FileUtil.readableFileSize(file.length()) + ")"));
        return result;
    }

    private File createLocalArchive(boolean includeLogs) {
        try {
            File backupDir = FileUtil.mkdir(new File(appDir, "backups").getAbsolutePath());
            File out = new File(backupDir, "wang-detective-web-" + LocalDateTime.now().format(BACKUP_TIME_FORMATTER) + ".zip");
            ZipFile zipFile = new ZipFile(out);

            addIfExists(zipFile, new File(appDir, ".env"));
            addIfExists(zipFile, new File(appDir, "application.yml"));
            addIfExists(zipFile, new File(appDir, "docker-compose.yml"));
            addIfExists(zipFile, new File(appDir, "data"));
            addIfExists(zipFile, new File(appDir, "keys"));
            addIfExists(zipFile, new File(appDir, "scripts"));
            if (includeLogs) {
                addIfExists(zipFile, new File(appDir, "logs"));
                addIfExists(zipFile, new File("/var/log/king-detective.log"));
            }
            return out;
        } catch (Exception e) {
            log.error("Create local backup archive failed", e);
            throw new OciException(-1, "创建本地备份包失败: " + e.getMessage());
        }
    }

    private void addIfExists(ZipFile zipFile, File file) throws Exception {
        if (!file.exists()) {
            return;
        }
        if (file.isDirectory()) {
            zipFile.addFolder(file);
        } else {
            zipFile.addFile(file);
        }
    }

    private OracleInstanceFetcher createFetcher(String ociCfgId) {
        SysUserDTO user = sysService.getOciUser(ociCfgId);
        return new OracleInstanceFetcher(user);
    }

    private ObjectStorageClient createClient(OracleInstanceFetcher fetcher) {
        return ObjectStorageClient.builder().build(fetcher.getAuthenticationDetailsProvider());
    }

    private String getNamespace(ObjectStorageClient client) {
        return client.getNamespace(GetNamespaceRequest.builder().build()).getValue();
    }

    private Integer limit(Integer value) {
        if (value == null) {
            return 100;
        }
        return Math.max(1, Math.min(value, 1000));
    }

    private String normalizeObjectName(String prefix, String fileName) {
        String normalizedPrefix = StrUtil.blankToDefault(prefix, "wang-detective/backups").trim();
        normalizedPrefix = normalizedPrefix.replace("\\", "/");
        while (normalizedPrefix.startsWith("/")) {
            normalizedPrefix = normalizedPrefix.substring(1);
        }
        while (normalizedPrefix.endsWith("/")) {
            normalizedPrefix = normalizedPrefix.substring(0, normalizedPrefix.length() - 1);
        }
        return normalizedPrefix + "/" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd")) + "/" + fileName;
    }

    private LocalDateTime toLocalDateTime(Date date) {
        if (date == null) {
            return null;
        }
        return LocalDateTime.ofInstant(date.toInstant(), ZoneId.systemDefault());
    }

    private String md5Hex(File file) {
        try (InputStream inputStream = new FileInputStream(file)) {
            MessageDigest digest = MessageDigest.getInstance("MD5");
            byte[] buffer = new byte[8192];
            int len;
            while ((len = inputStream.read(buffer)) > 0) {
                digest.update(buffer, 0, len);
            }
            StringBuilder hex = new StringBuilder();
            for (byte b : digest.digest()) {
                String item = Integer.toHexString(0xff & b);
                if (item.length() == 1) {
                    hex.append('0');
                }
                hex.append(item);
            }
            return hex.toString();
        } catch (Exception e) {
            return null;
        }
    }
}
