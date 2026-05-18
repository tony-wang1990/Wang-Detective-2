package com.tony.kingdetective.bean.params.oci.objectstorage;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ObjectStorageBackupParams {
    @NotBlank(message = "OCI config id cannot be blank")
    private String ociCfgId;

    private String bucketName;

    private String prefix = "wang-detective/backups";

    private Boolean includeLogs = false;

    private Boolean uploadToObjectStorage = true;
}
