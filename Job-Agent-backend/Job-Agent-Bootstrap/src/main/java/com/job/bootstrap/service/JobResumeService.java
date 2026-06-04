package com.job.bootstrap.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.job.common.entity.resume.JobResume;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.List;

/**
 * 作者:hfj
 * 功能:简历业务服务接口，定义简历上传和当前用户简历列表查询能力
 * 日期:2026/6/4 10:30
 */
public interface JobResumeService extends IService<JobResume> {

    /**
     * 上传简历。
     * P表示参数描述，同一个用户可以上传多份简历，但是 resumeName 不能重复。
     *
     * @param userId 当前登录用户 ID
     * @param resumeName 简历名称
     * @param file 前端上传的 PDF、DOC 或 DOCX 文件
     * @return 返回保存后的简历实体
     */
    JobResume uploadResume(Long userId, String resumeName, MultipartFile file);

    /**
     * 查询当前用户的简历列表。
     *
     * @param userId 当前登录用户 ID
     * @return 返回该用户未删除的简历列表
     */
    List<JobResume> listUserResumes(Long userId);

    /**
     * 查询当前用户的指定简历。
     *
     * @param userId 当前登录用户 ID
     * @param resumeId 简历 ID
     * @return 返回简历实体，不存在或不属于当前用户时抛出业务异常
     */
    JobResume getUserResumeRequired(Long userId, Long resumeId);

    /**
     * 从 MinIO 打开简历文件流。
     *
     * @param resume 数据库中的简历实体
     * @return 返回简历文件输入流，由 Controller 交给浏览器读取
     */
    InputStream openResumeFile(JobResume resume);
}
