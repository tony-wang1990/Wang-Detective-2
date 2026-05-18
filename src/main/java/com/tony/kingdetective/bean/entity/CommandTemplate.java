package com.tony.kingdetective.bean.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("ops_command_template")
public class CommandTemplate {
    @TableId
    private String id;

    private String name;

    private String command;

    private String category;

    private String description;

    @TableField("risk_level")
    private String riskLevel;

    @TableField("create_time")
    private LocalDateTime createTime;

    @TableField("update_time")
    private LocalDateTime updateTime;
}
