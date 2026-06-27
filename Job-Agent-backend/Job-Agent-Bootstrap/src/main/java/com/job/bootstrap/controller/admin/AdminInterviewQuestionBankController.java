package com.job.bootstrap.controller.admin;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.job.bootstrap.service.InterviewQuestionBankService;
import com.job.common.dto.interview.InterviewQuestionBankQueryDTO;
import com.job.common.dto.interview.InterviewQuestionImportDTO;
import com.job.common.entity.base.Result;
import com.job.common.entity.base.ResultCodeEnum;
import com.job.common.vo.interview.InterviewQuestionBankVO;
import com.job.common.vo.interview.InterviewQuestionImportResultVO;
import com.job.common.vo.rag.RagIndexResultVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 后台 AI 模拟面试题库管理接口。
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/admin/interview/question-bank")
public class AdminInterviewQuestionBankController {

    private final InterviewQuestionBankService questionBankService;

    /**
     * 从服务端本地 markdown 目录导入题库，并可选择同步写入 RAG。
     */
    @PostMapping("/import-local")
    public Result<InterviewQuestionImportResultVO> importLocal(@RequestBody(required = false) InterviewQuestionImportDTO dto) {
        return Result.build(questionBankService.importLocalMarkdown(dto), ResultCodeEnum.SUCCESS);
    }

    /**
     * 后台分页查看题库。
     */
    @GetMapping("/page")
    public Result<IPage<InterviewQuestionBankVO>> page(InterviewQuestionBankQueryDTO query) {
        return Result.build(questionBankService.pageQuestions(query), ResultCodeEnum.SUCCESS);
    }

    /**
     * 查看单道题详情和标准答案。
     */
    @GetMapping("/{id}")
    public Result<InterviewQuestionBankVO> detail(@PathVariable Long id) {
        return Result.build(questionBankService.getDetail(id), ResultCodeEnum.SUCCESS);
    }

    /**
     * 启用或禁用题目。
     */
    @PutMapping("/{id}/status")
    public Result<Void> updateStatus(@PathVariable Long id, @RequestParam String status) {
        questionBankService.updateStatus(id, status);
        return Result.build(null, ResultCodeEnum.SUCCESS);
    }

    /**
     * 重建单道题的 RAG 索引。
     */
    @PostMapping("/{id}/index")
    public Result<RagIndexResultVO> indexQuestion(@PathVariable Long id) {
        return Result.build(questionBankService.indexQuestion(id), ResultCodeEnum.SUCCESS);
    }

    /**
     * 重建全部启用题目的 RAG 索引。
     */
    @PostMapping("/index-all")
    public Result<RagIndexResultVO> indexAllActive() {
        return Result.build(questionBankService.indexAllActive(), ResultCodeEnum.SUCCESS);
    }
}
