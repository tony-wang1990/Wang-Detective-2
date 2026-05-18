package com.tony.kingdetective.bean.params.oci.objectstorage;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ObjectStorageListObjectsParams {
    @NotBlank(message = "OCI config id cannot be blank")
    private String ociCfgId;

    @NotBlank(message = "Bucket name cannot be blank")
    private String bucketName;

    private String prefix = "wang-detective/backups";

    private Integer limit = 100;
}
