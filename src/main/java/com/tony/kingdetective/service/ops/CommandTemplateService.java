package com.tony.kingdetective.service.ops;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.tony.kingdetective.bean.entity.CommandTemplate;
import com.tony.kingdetective.bean.params.ops.CommandTemplateSaveParams;
import com.tony.kingdetective.exception.OciException;
import com.tony.kingdetective.mapper.CommandTemplateMapper;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class CommandTemplateService {
    private final CommandTemplateMapper mapper;

    public CommandTemplateService(CommandTemplateMapper mapper) {
        this.mapper = mapper;
    }

    public List<CommandTemplate> list(String keyword, String category) {
        LambdaQueryWrapper<CommandTemplate> wrapper = new LambdaQueryWrapper<CommandTemplate>()
                .orderByAsc(CommandTemplate::getCategory)
                .orderByDesc(CommandTemplate::getUpdateTime)
                .orderByDesc(CommandTemplate::getCreateTime);
        if (StrUtil.isNotBlank(category)) {
            wrapper.eq(CommandTemplate::getCategory, category.trim());
        }
        if (StrUtil.isNotBlank(keyword)) {
            String word = keyword.trim();
            wrapper.and(w -> w.like(CommandTemplate::getName, word)
                    .or()
                    .like(CommandTemplate::getCommand, word)
                    .or()
                    .like(CommandTemplate::getDescription, word)
                    .or()
                    .like(CommandTemplate::getCategory, word));
        }
        return mapper.selectList(wrapper);
    }

    public CommandTemplate create(CommandTemplateSaveParams params) {
        validate(params);
        CommandTemplate template = new CommandTemplate();
        template.setId(IdUtil.fastSimpleUUID());
        template.setCreateTime(LocalDateTime.now());
        apply(template, params);
        mapper.insert(template);
        return template;
    }

    public CommandTemplate update(String id, CommandTemplateSaveParams params) {
        validate(params);
        CommandTemplate template = mapper.selectById(id);
        if (template == null) {
            throw new OciException(-1, "Command template not found");
        }
        apply(template, params);
        mapper.updateById(template);
        return template;
    }

    public void delete(String id) {
        mapper.deleteById(id);
    }

    private void apply(CommandTemplate template, CommandTemplateSaveParams params) {
        template.setName(params.getName().trim());
        template.setCommand(params.getCommand().trim());
        template.setCategory(StrUtil.blankToDefault(StrUtil.trim(params.getCategory()), "常用"));
        template.setDescription(StrUtil.emptyToNull(StrUtil.trim(params.getDescription())));
        template.setRiskLevel(normalizeRisk(params.getRiskLevel()));
        template.setUpdateTime(LocalDateTime.now());
    }

    private void validate(CommandTemplateSaveParams params) {
        if (params == null || StrUtil.isBlank(params.getName()) || StrUtil.isBlank(params.getCommand())) {
            throw new OciException(-1, "Template name and command are required");
        }
    }

    private String normalizeRisk(String riskLevel) {
        String value = StrUtil.blankToDefault(riskLevel, "LOW").trim().toUpperCase();
        return switch (value) {
            case "LOW", "MEDIUM", "HIGH" -> value;
            default -> "LOW";
        };
    }
}
