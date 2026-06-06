package com.job.bootstrap.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.job.common.dto.position.PositionSaveDTO;
import com.job.common.entity.position.JobPosition;
import com.job.common.vo.position.PositionImportVO;
import org.springframework.web.multipart.MultipartFile;

/**
 * 作者:hfj
 * 功能:岗位业务服务接口，定义岗位分页、增删改查、发布下架和 Excel 导入能力
 * 日期:2026/6/6 15:20
 */
public interface JobPositionService extends IService<JobPosition> {

    /**
     * 分页查询岗位列表。
     *
     * @param pageNo 当前页码
     * @param pageSize 每页条数
     * @param keyword 搜索关键词
     * @param companyId 公司ID
     * @param city 工作城市
     * @param district 工作区县
     * @param jobCategory 岗位类别
     * @param educationReq 学历要求
     * @param experienceReq 经验要求
     * @param workType 工作类型
     * @param status 岗位状态
     * @param onlyPublished 是否只查询已发布岗位
     * @return 返回 MyBatis-Plus 分页对象
     */
    IPage<JobPosition> pagePositions(Long pageNo, Long pageSize, String keyword, Long companyId, String city,
                                     String district, String jobCategory, String educationReq, String experienceReq,
                                     String workType, Integer status, boolean onlyPublished);

    /**
     * 新增岗位。
     *
     * @param request 岗位表单参数
     * @return 返回保存后的岗位实体
     */
    JobPosition createPosition(PositionSaveDTO request);

    /**
     * 修改岗位。
     *
     * @param positionId 岗位ID
     * @param request 岗位表单参数
     * @return 返回修改后的岗位实体
     */
    JobPosition updatePosition(Long positionId, PositionSaveDTO request);

    /**
     * 逻辑删除岗位。
     *
     * @param positionId 岗位ID
     */
    void deletePosition(Long positionId);

    /**
     * 发布岗位。
     *
     * @param positionId 岗位ID
     * @return 返回发布后的岗位实体
     */
    JobPosition publishPosition(Long positionId);

    /**
     * 下架岗位。
     *
     * @param positionId 岗位ID
     * @return 返回下架后的岗位实体
     */
    JobPosition offlinePosition(Long positionId);

    /**
     * 根据 ID 查询岗位，不存在时抛出业务异常。
     *
     * @param positionId 岗位ID
     * @return 返回岗位实体
     */
    JobPosition getPositionRequired(Long positionId);

    /**
     * 从 Excel 导入岗位数据。
     *
     * @param file 前端上传的 xls 或 xlsx 文件
     * @return 返回导入统计信息
     */
    PositionImportVO importPositions(MultipartFile file);
}
