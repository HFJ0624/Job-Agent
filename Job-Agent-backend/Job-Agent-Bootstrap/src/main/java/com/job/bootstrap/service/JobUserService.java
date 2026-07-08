package com.job.bootstrap.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.job.common.entity.user.JobUser;

/**
 * 用户业务服务接口。
 *
 * <p>核心职责：管理平台用户的全生命周期，包括注册、登录、资料修改及后台分页查询。</p>
 *
 * <p>所属业务模块：用户中心 - 账号管理</p>
 *
 * <p>主要调用链：
 * UserController / AuthController / AdminUserController -&gt; JobUserService -&gt; JobUserServiceImpl -&gt; JobUserRepository / PasswordEncoder</p>
 */
public interface JobUserService extends IService<JobUser> {

    /**
     * 注册用户。
     * P表示参数描述，Service 会负责唯一性校验和密码加密。
     *
     * @param user 用户实体，包含前端提交的注册信息
     * @return 返回注册成功后的用户实体
     */
    JobUser register(JobUser user);

    /**
     * 用户登录。
     *
     * @param account 登录账号，可以是用户名、手机号或邮箱
     * @param rawPassword 前端提交的明文密码
     * @return 返回登录成功的用户实体
     */
    JobUser login(String account, String rawPassword);

    /**
     * 根据 ID 查询用户。
     *
     * @param userId 用户 ID
     * @return 返回用户实体，不存在时抛出业务异常
     */
    JobUser getUserRequired(Long userId);

    /**
     * 修改当前登录用户资料。
     *
     * @param userId 当前登录用户 ID
     * @param profile 待修改的资料字段
     * @return 返回修改后的用户实体
     */
    JobUser updateProfile(Long userId, JobUser profile);

    /**
     * 分页查询用户。
     *
     * @param pageNo 当前页码
     * @param pageSize 每页条数
     * @param keyword 搜索关键词，可以为空
     * @return 返回 MyBatis-Plus 分页对象
     */
    IPage<JobUser> pageUsers(Long pageNo, Long pageSize, String keyword);
}
