package com.job.common.vo.company;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 作者:hfj
 * 功能:公司 Excel 导入结果响应对象，告诉管理端导入了多少、失败了多少
 * 日期:2026/6/6 10:30
 */
@Data
public class CompanyImportVO {

    /**
     * Excel 中被读取到的数据行数量，不包含表头和空行。
     */
    private int totalRows;

    /**
     * 新增成功数量。
     */
    private int insertCount;

    /**
     * 更新成功数量，重复公司名称会按更新处理。
     */
    private int updateCount;

    /**
     * 失败数量，例如公司名称为空、数字格式错误等。
     */
    private int failureCount;

    /**
     * 失败明细，最多保存前若干条，方便前端展示问题。
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
