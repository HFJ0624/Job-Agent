package com.job.bootstrap.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.job.common.entity.address.JobUserAddress;

/**
 * 用户地址业务服务接口。
 *
 * <p>核心职责：管理用户家庭地址的查询与保存，支持默认地址的自动维护。</p>
 *
 * <p>所属业务模块：用户中心 - 地址管理</p>
 *
 * <p>主要调用链：
 * UserAddressController / JobApplicationService -&gt; JobUserAddressService -&gt; JobUserAddressServiceImpl -&gt; JobUserAddressRepository</p>
 */
public interface JobUserAddressService extends IService<JobUserAddress> {

    /**
     * 查询当前用户默认家庭地址。
     *
     * @param userId 当前登录用户 ID
     * @return 返回默认地址，没有填写时返回 null
     */
    JobUserAddress getDefaultAddress(Long userId);

    /**
     * 保存当前用户家庭地址。
     * P表示参数描述，如果用户已经有默认地址就更新，没有默认地址就新增。
     *
     * @param userId 当前登录用户 ID
     * @param address 前端提交的地址实体
     * @return 返回保存后的地址实体
     */
    JobUserAddress saveDefaultAddress(Long userId, JobUserAddress address);
}
