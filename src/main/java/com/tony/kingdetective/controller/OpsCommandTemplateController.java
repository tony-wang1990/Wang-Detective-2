package com.tony.kingdetective.controller;

import com.tony.kingdetective.bean.ResponseData;
import com.tony.kingdetective.bean.entity.CommandTemplate;
import com.tony.kingdetective.bean.params.ops.CommandTemplateSaveParams;
import com.tony.kingdetective.service.IAuditLogService;
import com.tony.kingdetective.service.ops.CommandTemplateService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/ops/templates")
public class OpsCommandTemplateController {
    private final CommandTemplateService commandTemplateService;
    private final IAuditLogService auditLogService;

    public OpsCommandTemplateController(CommandTemplateService commandTemplateService,
                                        IAuditLogService auditLogService) {
        this.commandTemplateService = commandTemplateService;
        this.auditLogService = auditLogService;
    }

    @GetMapping
    public ResponseData<List<CommandTemplate>> list(@RequestParam(value = "keyword", required = false) String keyword,
                                                    @RequestParam(value = "category", required = false) String category) {
        return ResponseData.successData(commandTemplateService.list(keyword, category));
    }

    @PostMapping
    public ResponseData<CommandTemplate> create(@Valid @RequestBody CommandTemplateSaveParams params) {
        CommandTemplate template = commandTemplateService.create(params);
        auditLogService.logSuccess(null, "OPS_COMMAND_TEMPLATE_CREATE", template.getId(), template.getName());
        return ResponseData.successData(template);
    }

    @PutMapping("/{id}")
    public ResponseData<CommandTemplate> update(@PathVariable("id") String id,
                                                @Valid @RequestBody CommandTemplateSaveParams params) {
        CommandTemplate template = commandTemplateService.update(id, params);
        auditLogService.logSuccess(null, "OPS_COMMAND_TEMPLATE_UPDATE", id, template.getName());
        return ResponseData.successData(template);
    }

    @DeleteMapping("/{id}")
    public ResponseData<Void> delete(@PathVariable("id") String id) {
        commandTemplateService.delete(id);
        auditLogService.logSuccess(null, "OPS_COMMAND_TEMPLATE_DELETE", id, null);
        return ResponseData.successData();
    }
}
