package com.tony.kingdetective.bean.params.oci.objectstorage;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ObjectStorageObjectParams {
    @NotBlank(message = "OCI config id cannot be blank")
    private String ociCfgId;

    @NotBlank(message = "Bucket name cannot be blank")
    private String bucketName;

    @NotBlank(message = "Object name cannot be blank")
    private String objectName;
}
