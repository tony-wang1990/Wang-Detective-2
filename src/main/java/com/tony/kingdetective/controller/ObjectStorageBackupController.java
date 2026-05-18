package com.tony.kingdetective.controller;

import com.tony.kingdetective.bean.ResponseData;
import com.tony.kingdetective.bean.params.oci.objectstorage.ObjectStorageBackupParams;
import com.tony.kingdetective.bean.params.oci.objectstorage.ObjectStorageListObjectsParams;
import com.tony.kingdetective.bean.params.oci.objectstorage.ObjectStorageObjectParams;
import com.tony.kingdetective.bean.response.oci.objectstorage.ObjectStorageBackupRsp;
import com.tony.kingdetective.service.oci.ObjectStorageBackupService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/backups")
public class ObjectStorageBackupController {

    private final ObjectStorageBackupService backupService;

    public ObjectStorageBackupController(ObjectStorageBackupService backupService) {
        this.backupService = backupService;
    }

    @GetMapping("/buckets")
    public ResponseData<List<ObjectStorageBackupRsp.BucketInfo>> buckets(@RequestParam("ociCfgId") String ociCfgId) {
        return ResponseData.successData(backupService.listBuckets(ociCfgId));
    }

    @PostMapping("/objects")
    public ResponseData<ObjectStorageBackupRsp.ObjectList> objects(@RequestBody @Valid ObjectStorageListObjectsParams params) {
        return ResponseData.successData(backupService.listObjects(params));
    }

    @PostMapping("/archive")
    public ResponseData<ObjectStorageBackupRsp> archive(@RequestBody @Valid ObjectStorageBackupParams params) {
        return ResponseData.successData(backupService.createBackup(params));
    }

    @PostMapping("/delete-object")
    public ResponseData<Void> deleteObject(@RequestBody @Valid ObjectStorageObjectParams params) {
        backupService.deleteObject(params);
        return ResponseData.successData();
    }
}
