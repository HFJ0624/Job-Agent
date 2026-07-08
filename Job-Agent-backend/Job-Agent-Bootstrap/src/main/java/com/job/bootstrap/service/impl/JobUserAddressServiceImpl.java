package com.job.bootstrap.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.job.bootstrap.mapper.JobUserAddressMapper;
import com.job.bootstrap.service.JobUserAddressService;
import com.job.common.entity.address.JobUserAddress;
import com.job.common.entity.base.ResultCodeEnum;
import com.job.exception.BizException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Date;

/**
 * 用户地址业务服务实现类。
 *
 * <p>核心职责：负责用户家庭地址（JobUserAddress）的查询与保存。
 * 提供默认地址查询、地址归属校验、字段清洗与强制更新能力。</p>
 *
 * <p>所属业务模块：用户中心模块（User Center）- 地址子模块</p>
 *
 * <p>主要调用链：
 * <pre>
 * JobUserAddressController -&gt; JobUserAddressService -&gt; JobUserAddressServiceImpl
 *                                         |
 *                                         v
 *                              JobUserAddressMapper
 * </pre></p>
 *
 * <p>与其他核心组件的关系：
 * <ul>
 *   <li>继承 {@link ServiceImpl}，依赖 {@link JobUserAddressMapper} 进行地址持久化操作</li>
 *   <li>被个人中心等模块调用，用于通勤范围计算与推荐排序</li>
 * </ul></p>
 *
 * <p>设计说明：
 * <ul>
 *   <li>写操作使用 {@link Transactional} 保证事务一致性</li>
 *   <li>每个用户仅维护一份默认家庭地址，不存在时自动新增，存在时强制更新</li>
 *   <li>地址字段（省、市、区、详细地址、经纬度）为空时也要显式写入数据库，避免旧数据残留</li>
 *   <li>保存前进行字段清洗与内容非空校验，防止无效数据入库</li>
 * </ul></p>
 *
 * @author hfj
 * @since 2026/6/4
 */
@Service
public class JobUserAddressServiceImpl extends ServiceImpl<JobUserAddressMapper, JobUserAddress>
        implements JobUserAddressService {

    /**
     * 默认地址标记。
     */
    private static final int DEFAULT_ADDRESS = 1;

    /**
     * 逻辑未删除状态。
     */
    private static final int NOT_DELETED = 0;

    /**
     * 默认地址名称。
     */
    private static final String DEFAULT_ADDRESS_NAME = "家庭地址";

    /**
     * 查询当前用户默认家庭地址。
     *
     * <p>按用户 ID 查询未删除的默认地址，按更新时间倒序取第一条；未填写时返回 null。</p>
     *
     * @param userId 当前登录用户 ID
     * @return 返回默认地址，没有填写时返回 null
     */
    @Override
    public JobUserAddress getDefaultAddress(Long userId) {
        // 1. 个人中心只展示当前用户自己的默认地址。
        return getOne(new LambdaQueryWrapper<JobUserAddress>()
                .eq(JobUserAddress::getUserId, userId)
                .eq(JobUserAddress::getIsDefault, DEFAULT_ADDRESS)
                .eq(JobUserAddress::getIsDeleted, NOT_DELETED)
                .orderByDesc(JobUserAddress::getUpdateTime)
                .last("limit 1"), false);
    }

    /**
     * 保存当前用户家庭地址。
     *
     * <p>支持手动填写与高德地图选择两种录入方式；先清洗字段并校验内容非空，
     * 再按地址 ID 查询已有记录进行更新，无记录则自动新增。更新时使用强制写入确保空值也能覆盖旧数据。</p>
     *
     * @param userId 当前登录用户 ID
     * @param address 前端提交的地址实体
     * @return 返回保存后的地址实体
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public JobUserAddress saveDefaultAddress(Long userId, JobUserAddress address) {
        try {
            // 1. 先清洗字符串字段，避免全是空格的数据进入数据库。
            cleanAddress(address);

            // 2. 如果前端没有填写任何地址内容，直接提示用户补充。
            if (!hasAddressContent(address)) {
                throw new BizException("请填写家庭地址");
            }

            // 3. 打印本次收到的地址字段，方便确认前端是否真的把省市区传到了后端。
            printAddressSaveInfo("收到前端地址参数", userId, address);

            // 4. 优先按前端传来的地址 ID 查询；没有 ID 时使用当前用户已有默认地址。
            JobUserAddress dbAddress = findEditableAddress(userId, address.getId());
            Date now = new Date();

            if (dbAddress == null) {
                dbAddress = new JobUserAddress();
                dbAddress.setUserId(userId);
                dbAddress.setCreateTime(now);
                dbAddress.setIsDeleted(NOT_DELETED);
                copyAddressFields(dbAddress, address, now);
                save(dbAddress);
            } else {
                copyAddressFields(dbAddress, address, now);
                forceUpdateAddressFields(dbAddress);
            }

            printAddressSaveInfo("写入数据库地址字段", userId, dbAddress);
            return dbAddress;
        } catch (BizException exception) {
            throw exception;
        } catch (Exception exception) {
            printAddressSaveError(userId, address, exception);
            throw new BizException(ResultCodeEnum.SYSTEM_ERROR.getCode(), "家庭地址保存失败：" + exception.getMessage(), exception);
        }
    }

    /**
     * 复制允许用户维护的地址字段。
     *
     * <p>不复制 userId、isDeleted 等归属字段，仅复制地址内容与默认标记。</p>
     *
     * @param target 数据库地址实体
     * @param source 前端提交的地址实体
     * @param now 当前时间
     */
    private void copyAddressFields(JobUserAddress target, JobUserAddress source, Date now) {
        // 1. userId、isDeleted 等归属字段不从前端接收，只复制地址内容字段。
        target.setAddressName(defaultIfBlank(source.getAddressName(), DEFAULT_ADDRESS_NAME));
        target.setProvince(source.getProvince());
        target.setCity(source.getCity());
        target.setDistrict(source.getDistrict());
        target.setDetailAddress(source.getDetailAddress());
        target.setLongitude(source.getLongitude());
        target.setLatitude(source.getLatitude());
        target.setIsDefault(DEFAULT_ADDRESS);
        target.setUpdateTime(now);
    }

    /**
     * 强制更新地址字段。
     *
     * <p>使用 {@link LambdaUpdateWrapper} 进行显式字段设置，确保省、市、区、详细地址、
     * 经纬度等字段即使传入 null 也能覆盖数据库旧值，避免残留脏数据。</p>
     *
     * @param address 待更新地址实体
     */
    private void forceUpdateAddressFields(JobUserAddress address) {
        update(new LambdaUpdateWrapper<JobUserAddress>()
                .eq(JobUserAddress::getId, address.getId())
                .eq(JobUserAddress::getUserId, address.getUserId())
                .eq(JobUserAddress::getIsDeleted, NOT_DELETED)
                .set(JobUserAddress::getAddressName, address.getAddressName())
                .set(JobUserAddress::getProvince, address.getProvince())
                .set(JobUserAddress::getCity, address.getCity())
                .set(JobUserAddress::getDistrict, address.getDistrict())
                .set(JobUserAddress::getDetailAddress, address.getDetailAddress())
                .set(JobUserAddress::getLongitude, address.getLongitude())
                .set(JobUserAddress::getLatitude, address.getLatitude())
                .set(JobUserAddress::getIsDefault, address.getIsDefault())
                .set(JobUserAddress::getUpdateTime, address.getUpdateTime()));
    }

    /**
     * 查询可编辑的地址。
     *
     * @param userId 当前登录用户 ID
     * @param addressId 前端传来的地址 ID
     * @return 返回可以编辑的地址，不存在时返回 null
     */
    private JobUserAddress findEditableAddress(Long userId, Long addressId) {
        if (addressId != null) {
            // 1. 带 userId 条件查询，确保只能更新自己的地址。
            JobUserAddress address = getOne(new LambdaQueryWrapper<JobUserAddress>()
                    .eq(JobUserAddress::getId, addressId)
                    .eq(JobUserAddress::getUserId, userId)
                    .eq(JobUserAddress::getIsDeleted, NOT_DELETED), false);
            if (address == null) {
                throw new BizException("地址不存在");
            }
            return address;
        }
        return getDefaultAddress(userId);
    }

    /**
     * 清洗地址字符串字段。
     *
     * @param address 地址实体
     */
    private void cleanAddress(JobUserAddress address) {
        address.setAddressName(trimToNull(address.getAddressName()));
        address.setProvince(trimToNull(address.getProvince()));
        address.setCity(trimToNull(address.getCity()));
        address.setDistrict(trimToNull(address.getDistrict()));
        address.setDetailAddress(trimToNull(address.getDetailAddress()));
    }

    /**
     * 判断地址是否有有效内容。
     *
     * @param address 地址实体
     * @return true 表示有内容，false 表示完全没填
     */
    private boolean hasAddressContent(JobUserAddress address) {
        return StringUtils.hasText(address.getProvince())
                || StringUtils.hasText(address.getCity())
                || StringUtils.hasText(address.getDistrict())
                || StringUtils.hasText(address.getDetailAddress())
                || address.getLongitude() != null
                || address.getLatitude() != null;
    }

    /**
     * 字符串默认值工具。
     *
     * @param value 原始值
     * @param defaultValue 默认值
     * @return 有内容时返回原始值，否则返回默认值
     */
    private String defaultIfBlank(String value, String defaultValue) {
        if (!StringUtils.hasText(value)) {
            return defaultValue;
        }
        return value;
    }

    /**
     * 字符串清洗工具。
     *
     * @param value 原始字符串
     * @return 去掉首尾空格后的字符串；如果没有有效内容则返回 null
     */
    private String trimToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }

    /**
     * 打印地址保存过程中的字段值。
     *
     * @param title 日志标题
     * @param userId 当前登录用户 ID
     * @param address 地址实体
     */
    private void printAddressSaveInfo(String title, Long userId, JobUserAddress address) {
        System.out.println();
        System.out.println("========== Job-Agent " + title + " ==========");
        System.out.println("用户ID：" + userId);
        System.out.println("地址ID：" + address.getId());
        System.out.println("地址名称：" + address.getAddressName());
        System.out.println("省份：" + address.getProvince());
        System.out.println("城市：" + address.getCity());
        System.out.println("区县：" + address.getDistrict());
        System.out.println("详细地址：" + address.getDetailAddress());
        System.out.println("经纬度：" + address.getLongitude() + "," + address.getLatitude());
        System.out.println("==========================================");
        System.out.println();
    }

    /**
     * 打印地址保存失败的详细诊断信息。
     *
     * @param userId 当前登录用户 ID
     * @param address 前端提交的地址实体
     * @param exception 原始异常
     */
    private void printAddressSaveError(Long userId, JobUserAddress address, Exception exception) {
        System.err.println();
        System.err.println("========== Job-Agent 家庭地址保存异常 ==========");
        System.err.println("触发位置：com.job.bootstrap.service.impl.JobUserAddressServiceImpl.saveDefaultAddress");
        System.err.println("用户ID：" + userId);
        System.err.println("地址ID：" + address.getId());
        System.err.println("省市区：" + address.getProvince() + "/" + address.getCity() + "/" + address.getDistrict());
        System.err.println("详细地址：" + address.getDetailAddress());
        System.err.println("经纬度：" + address.getLongitude() + "," + address.getLatitude());
        System.err.println("异常类型：" + exception.getClass().getName());
        System.err.println("异常信息：" + exception.getMessage());
        exception.printStackTrace(System.err);
        System.err.println("==========================================");
        System.err.println();
    }
}
