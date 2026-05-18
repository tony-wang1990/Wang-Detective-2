package com.tony.kingdetective.service;

import com.tony.kingdetective.bean.entity.AuditLog;

import java.util.List;

/**
 * 审计日志服务接口
 * 
 * @author Tony Wang
 */
public interface IAuditLogService {
    
    /**
     * 记录审计日志
     *
     * @param log 审计日志
     */
    void log(AuditLog log);
    
    /**
     * 记录操作成功
     *
     * @param userId 用户ID
     * @param operation 操作类型
     * @param target 操作目标
     * @param details 操作详情
     */
    void logSuccess(String userId, String operation, String target, String details);
    
    /**
     * 记录操作失败
     *
     * @param userId 用户ID
     * @param operation 操作类型
     * @param target 操作目标
     * @param error 错误消息
     */
    void logFailure(String userId, String operation, String target, String error);

    List<AuditLog> recent(int limit);

    List<AuditLog> search(String keyword, Boolean success, String operation, int limit);

    String exportCsv(String keyword, Boolean success, String operation, int limit);
}
