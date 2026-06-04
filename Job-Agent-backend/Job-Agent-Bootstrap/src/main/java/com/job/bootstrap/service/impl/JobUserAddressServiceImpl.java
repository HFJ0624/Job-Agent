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
 * 作者:hfj
 * 功能:用户地址业务服务实现，处理家庭地址查询、归属校验和保存
 * 日期:2026/6/4 11:00
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
     * P表示参数描述，手动填写和高德地图选择都会走这个方法。
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
     * P表示参数描述，省、市、区这些字段即使之前为 null，也要明确写入数据库。
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
