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
     * 修改简历名称。
     * P表示参数描述，同一个用户下新的简历名称不能和其它未删除简历重复。
     *
     * @param userId 当前登录用户 ID
     * @param resumeId 简历 ID
     * @param resumeName 新的简历名称
     * @return 返回修改后的简历实体
     */
    JobResume updateResumeName(Long userId, Long resumeId, String resumeName);

    /**
     * 逻辑删除简历。
     * P表示参数描述，只把 isDeleted 改成 1，不删除数据库记录和 MinIO 文件，方便后续恢复或审计。
     *
     * @param userId 当前登录用户 ID
     * @param resumeId 简历 ID
     */
    void deleteResume(Long userId, Long resumeId);

    /**
     * 设置默认简历。
     * P表示参数描述，一个用户只能有一份默认简历，设置前会先取消其它简历默认状态。
     *
     * @param userId 当前登录用户 ID
     * @param resumeId 简历 ID
     * @return 返回设置后的默认简历实体
     */
    JobResume setDefaultResume(Long userId, Long resumeId);

    /**
     * 解析简历文本。
     * P表示参数描述，后端会从 MinIO 读取文件，用 Tika/PDFBox/POI 抽取文本并写入 rawText 字段。
     *
     * @param userId 当前登录用户 ID
     * @param resumeId 简历 ID
     * @return 返回解析后的简历实体；解析失败时 status 为 PARSE_FAILED，rawText 保存失败原因
     */
    JobResume parseResumeText(Long userId, Long resumeId);

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
