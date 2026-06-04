package com.job.bootstrap.controller.front;

import cn.dev33.satoken.stp.StpUtil;
import com.job.bootstrap.service.JobUserAddressService;
import com.job.common.dto.address.SaveUserAddressDTO;
import com.job.common.entity.address.JobUserAddress;
import com.job.common.entity.base.Result;
import com.job.common.entity.base.ResultCodeEnum;
import com.job.common.vo.address.UserAddressVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 作者:hfj
 * 功能:用户家庭地址接口，支持查询和保存当前登录用户的默认地址
 * 日期:2026/6/4 11:00
 */
@Validated
@RestController
@RequestMapping("/front/user/address")
@RequiredArgsConstructor
public class UserAddressController {

    private final JobUserAddressService jobUserAddressService;

    /**
     * 查询当前登录用户的默认家庭地址。
     *
     * @return 返回用户默认地址；没有填写时 data 为 null
     */
    @GetMapping("/default")
    public Result<UserAddressVO> defaultAddress() {
        // 1. 从 Sa-Token 读取当前用户 ID，确保用户只能查询自己的地址。
        Long userId = StpUtil.getLoginIdAsLong();

        // 2. 查询默认地址并转换成 VO 返回前端。
        JobUserAddress address = jobUserAddressService.getDefaultAddress(userId);
        return Result.build(UserAddressVO.from(address), ResultCodeEnum.SUCCESS);
    }

    /**
     * 保存当前登录用户的默认家庭地址。
     * P表示参数描述，前端可以手动填写，也可以通过高德地图选择后回填。
     *
     * @param request 地址保存请求参数
     * @return 返回保存后的地址信息
     */
    @PutMapping("/default")
    public Result<UserAddressVO> saveDefaultAddress(@Valid @RequestBody SaveUserAddressDTO request) {
        // 1. 地址归属始终来自当前登录态，不从前端接收 userId，避免越权。
        Long userId = StpUtil.getLoginIdAsLong();

        // 2. 调用 Service 保存家庭地址。
        JobUserAddress savedAddress = jobUserAddressService.saveDefaultAddress(userId, request.toEntity(userId));
        return Result.build(UserAddressVO.from(savedAddress), ResultCodeEnum.SUCCESS);
    }
}
