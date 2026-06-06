package com.job.common.vo.position;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 作者:hfj
 * 功能:岗位 Excel 导入结果响应对象，告诉管理端导入成功、更新和失败的数量
 * 日期:2026/6/6 15:20
 */
@Data
public class PositionImportVO {

    /**
     * Excel 中读取到的数据行数量，不包含表头和空行。
     */
    private int totalRows;

    /**
     * 新增成功数量。
     */
    private int insertCount;

    /**
     * 更新成功数量，重复的公司+岗位+城市会按更新处理。
     */
    private int updateCount;

    /**
     * 导入失败数量。
     */
    private int failureCount;

    /**
     * 失败明细，前端会直接展示给管理员排查 Excel 问题。
     */
    private List<String> failureMessages = new ArrayList<>();

    /**
     * 记录一条失败信息。
     *
     * @param message 失败原因
     */
    public void addFailure(String message) {
        failureCount++;
        failureMessages.add(message);
    }

    /**
     * 记录一条新增成功。
     */
    public void addInsert() {
        insertCount++;
    }

    /**
     * 记录一条更新成功。
     */
    public void addUpdate() {
        updateCount++;
    }
}
