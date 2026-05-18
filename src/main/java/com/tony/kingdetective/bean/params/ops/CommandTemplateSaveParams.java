package com.tony.kingdetective.bean.params.ops;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CommandTemplateSaveParams {
    @NotBlank(message = "Template name cannot be blank")
    private String name;

    @NotBlank(message = "Command cannot be blank")
    private String command;

    private String category;

    private String description;

    private String riskLevel = "LOW";
}
