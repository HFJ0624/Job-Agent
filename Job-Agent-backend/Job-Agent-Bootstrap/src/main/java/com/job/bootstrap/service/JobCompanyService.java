package com.job.bootstrap.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.job.common.dto.company.CompanySaveDTO;
import com.job.common.entity.company.JobCompany;
import com.job.common.vo.company.CompanyImportVO;
import org.springframework.web.multipart.MultipartFile;

/**
 * 公司业务服务接口。
 *
 * <p>核心职责：管理公司信息的全生命周期，包括分页查询、增删改查及批量 Excel 导入。</p>
 *
 * <p>所属业务模块：岗位管理 - 公司信息</p>
 *
 * <p>主要调用链：
 * JobCompanyController / JobPositionService -&gt; JobCompanyService -&gt; JobCompanyServiceImpl -&gt; JobCompanyRepository</p>
 */
public interface JobCompanyService extends IService<JobCompany> {

    /**
     * 分页查询公司列表。
     *
     * @param pageNo 当前页码
     * @param pageSize 每页条数
     * @param keyword 搜索关键词，可以为空
     * @param status 公司状态，可以为空
     * @return 返回 MyBatis-Plus 分页对象
     */
    IPage<JobCompany> pageCompanies(Long pageNo, Long pageSize, String keyword, Integer status);

    /**
     * 新增公司。
     *
     * @param request 公司表单请求参数
     * @return 返回保存后的公司实体
     */
    JobCompany createCompany(CompanySaveDTO request);

    /**
     * 修改公司。
     *
     * @param companyId 公司 ID
     * @param request 公司表单请求参数
     * @return 返回修改后的公司实体
     */
    JobCompany updateCompany(Long companyId, CompanySaveDTO request);

    /**
     * 逻辑删除公司。
     *
     * @param companyId 公司 ID
     */
    void deleteCompany(Long companyId);

    /**
     * 根据 ID 查询公司，不存在时抛出业务异常。
     *
     * @param companyId 公司 ID
     * @return 返回公司实体
     */
    JobCompany getCompanyRequired(Long companyId);

    /**
     * 从 Excel 导入公司数据。
     *
     * @param file 前端上传的 xls 或 xlsx 文件
     * @return 返回导入统计信息
     */
    CompanyImportVO importCompanies(MultipartFile file);
}
