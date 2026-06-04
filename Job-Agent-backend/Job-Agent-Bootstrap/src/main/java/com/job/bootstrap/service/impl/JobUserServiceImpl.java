package com.job.bootstrap.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.job.bootstrap.mapper.JobUserMapper;
import com.job.bootstrap.service.JobUserService;
import com.job.common.entity.base.ResultCodeEnum;
import com.job.common.entity.user.JobUser;
import com.job.exception.BizException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Date;
import java.util.Objects;

/**
 * 作者:hfj
 * 功能:用户业务服务实现，处理注册、登录、资料修改和用户分页查询
 * 日期:2026/6/2 10:45
 */
@Service
@RequiredArgsConstructor
public class JobUserServiceImpl extends ServiceImpl<JobUserMapper, JobUser> implements JobUserService {

    /**
     * 账号禁用状态。
     */
    private static final int STATUS_DISABLED = 0;

    /**
     * 账号正常状态。
     */
    private static final int STATUS_NORMAL = 1;

    /**
     * 逻辑未删除状态。
     */
    private static final int NOT_DELETED = 0;

    private final PasswordEncoder passwordEncoder;

    /**
     * 注册用户。
     * P表示参数描述，注册前会检查用户名、手机号、邮箱是否重复。
     *
     * @param user 前端提交后转换出来的用户实体
     * @return 返回保存后的用户实体
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public JobUser register(JobUser user) {
        // 1. 先去掉首尾空格，空字符串统一转成 null，避免脏数据入库。
        user.setUsername(trimToNull(user.getUsername()));
        user.setPhone(trimToNull(user.getPhone()));
        user.setEmail(trimToNull(user.getEmail()));

        // 2. 校验唯一性，用户名必填，手机号和邮箱有值时才校验。
        if (existsByUsername(user.getUsername())) {
            throw new BizException(ResultCodeEnum.USER_NAME_IS_EXISTS);
        }
        if (StringUtils.hasText(user.getPhone()) && existsByPhone(user.getPhone(), null)) {
            throw new BizException("手机号已经存在");
        }
        if (StringUtils.hasText(user.getEmail()) && existsByEmail(user.getEmail(), null)) {
            throw new BizException("邮箱已经存在");
        }

        // 3. 设置系统字段和默认状态，密码必须加密后再保存。
        Date now = new Date();
        user.setPassword(passwordEncoder.encode(user.getPassword()));
        user.setStatus(STATUS_NORMAL);
        user.setIsDeleted(NOT_DELETED);
        user.setCreateTime(now);
        user.setUpdateTime(now);

        save(user);
        return user;
    }

    /**
     * 用户登录。
     * P表示参数描述，account 可以是用户名、手机号或邮箱。
     *
     * @param account 登录账号
     * @param rawPassword 前端提交的明文密码
     * @return 返回登录成功的用户实体
     */
    @Override
    public JobUser login(String account, String rawPassword) {
        // 1. 账号统一去空格，按用户名、手机号、邮箱三个字段匹配。
        String normalizedAccount = trimToNull(account);
        JobUser user = getOne(new LambdaQueryWrapper<JobUser>()
                .eq(JobUser::getIsDeleted, NOT_DELETED)
                .and(wrapper -> wrapper
                        .eq(JobUser::getUsername, normalizedAccount)
                        .or()
                        .eq(JobUser::getPhone, normalizedAccount)
                        .or()
                        .eq(JobUser::getEmail, normalizedAccount)), false);

        // 2. 用户不存在时直接返回登录失败。
        if (user == null) {
            printLoginError("没有查询到匹配账号", normalizedAccount, null);
            throw new BizException(ResultCodeEnum.LOGIN_ERROR);
        }

        // 3. BCryptPasswordEncoder 只能校验 BCrypt 密文，数据库里不能保存 123456 这种明文。
        if (!isBCryptHash(user.getPassword())) {
            printLoginError("数据库密码不是 BCrypt 加密后的密文", normalizedAccount, user);
            throw new BizException(ResultCodeEnum.LOGIN_ERROR);
        }

        // 4. 密码不匹配时返回登录失败。
        if (!passwordEncoder.matches(rawPassword, user.getPassword())) {
            printLoginError("密码校验失败，前端输入的密码与数据库密文不匹配", normalizedAccount, user);
            throw new BizException(ResultCodeEnum.LOGIN_ERROR);
        }

        // 5. 禁用账号不允许登录。
        if (Objects.equals(user.getStatus(), STATUS_DISABLED)) {
            throw new BizException(ResultCodeEnum.ACCOUNT_STOP);
        }
        return user;
    }

    /**
     * 根据用户 ID 查询用户。
     *
     * @param userId 用户 ID
     * @return 返回用户实体，不存在时抛出业务异常
     */
    @Override
    public JobUser getUserRequired(Long userId) {
        // 1. 查询时固定过滤逻辑删除的数据。
        JobUser user = getOne(new LambdaQueryWrapper<JobUser>()
                .eq(JobUser::getId, userId)
                .eq(JobUser::getIsDeleted, NOT_DELETED), false);
        if (user == null) {
            throw new BizException("用户不存在");
        }
        return user;
    }

    /**
     * 修改当前登录用户资料。
     * P表示参数描述，只允许修改资料字段，不能通过这个方法改密码或账号状态。
     *
     * @param userId 当前登录用户 ID
     * @param profile 前端提交的资料字段
     * @return 返回修改后的用户实体
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public JobUser updateProfile(Long userId, JobUser profile) {
        // 1. 先查询数据库中的用户，确保用户存在。
        JobUser dbUser = getUserRequired(userId);

        // 2. 手机号和邮箱先清洗，再判断是否被其他用户占用。
        String newPhone = trimToNull(profile.getPhone());
        String newEmail = trimToNull(profile.getEmail());

        if (StringUtils.hasText(newPhone)
                && !Objects.equals(newPhone, dbUser.getPhone())
                && existsByPhone(newPhone, userId)) {
            throw new BizException("手机号已经被其他用户使用");
        }
        if (StringUtils.hasText(newEmail)
                && !Objects.equals(newEmail, dbUser.getEmail())
                && existsByEmail(newEmail, userId)) {
            throw new BizException("邮箱已经被其他用户使用");
        }

        // 3. 只允许修改个人资料字段，username、password、status、isDeleted 不从这个接口写入。
        dbUser.setNickname(trimToNull(profile.getNickname()));
        dbUser.setRealName(trimToNull(profile.getRealName()));
        dbUser.setPhone(newPhone);
        dbUser.setEmail(newEmail);
        dbUser.setAvatarUrl(trimToNull(profile.getAvatarUrl()));
        dbUser.setGender(profile.getGender());
        dbUser.setEducation(trimToNull(profile.getEducation()));
        dbUser.setWorkYears(profile.getWorkYears());
        dbUser.setUpdateTime(new Date());

        updateById(dbUser);
        return getUserRequired(userId);
    }

    /**
     * 分页查询用户列表。
     * P表示参数描述，keyword 为空时查询全部未删除用户。
     *
     * @param pageNo 当前页码
     * @param pageSize 每页条数
     * @param keyword 搜索关键词，可以为空
     * @return 返回用户分页结果
     */
    @Override
    public IPage<JobUser> pageUsers(Long pageNo, Long pageSize, String keyword) {
        // 1. 默认只查未删除用户，并按创建时间倒序展示。
        LambdaQueryWrapper<JobUser> wrapper = new LambdaQueryWrapper<JobUser>()
                .eq(JobUser::getIsDeleted, NOT_DELETED)
                .orderByDesc(JobUser::getCreateTime);

        // 2. 有关键词时，按用户名、昵称、手机号、邮箱模糊查询。
        if (StringUtils.hasText(keyword)) {
            String likeKeyword = keyword.trim();
            wrapper.and(query -> query
                    .like(JobUser::getUsername, likeKeyword)
                    .or()
                    .like(JobUser::getNickname, likeKeyword)
                    .or()
                    .like(JobUser::getPhone, likeKeyword)
                    .or()
                    .like(JobUser::getEmail, likeKeyword));
        }

        return page(new Page<>(pageNo, pageSize), wrapper);
    }

    /**
     * 判断用户名是否已经存在。
     *
     * @param username 用户名
     * @return true 表示已存在，false 表示不存在
     */
    private boolean existsByUsername(String username) {
        return count(new LambdaQueryWrapper<JobUser>()
                .eq(JobUser::getUsername, username)
                .eq(JobUser::getIsDeleted, NOT_DELETED)) > 0;
    }

    /**
     * 判断手机号是否已经存在。
     * P表示参数描述，修改资料时需要排除当前用户自己。
     *
     * @param phone 手机号
     * @param excludeUserId 需要排除的用户 ID，注册时可为空
     * @return true 表示已存在，false 表示不存在
     */
    private boolean existsByPhone(String phone, Long excludeUserId) {
        LambdaQueryWrapper<JobUser> wrapper = new LambdaQueryWrapper<JobUser>()
                .eq(JobUser::getPhone, phone)
                .eq(JobUser::getIsDeleted, NOT_DELETED);

        // 1. 编辑资料时，自己的手机号不算重复。
        if (excludeUserId != null) {
            wrapper.ne(JobUser::getId, excludeUserId);
        }
        return count(wrapper) > 0;
    }

    /**
     * 判断邮箱是否已经存在。
     *
     * @param email 邮箱
     * @param excludeUserId 需要排除的用户 ID，注册时可为空
     * @return true 表示已存在，false 表示不存在
     */
    private boolean existsByEmail(String email, Long excludeUserId) {
        LambdaQueryWrapper<JobUser> wrapper = new LambdaQueryWrapper<JobUser>()
                .eq(JobUser::getEmail, email)
                .eq(JobUser::getIsDeleted, NOT_DELETED);

        // 1. 编辑资料时，自己的邮箱不算重复。
        if (excludeUserId != null) {
            wrapper.ne(JobUser::getId, excludeUserId);
        }
        return count(wrapper) > 0;
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
     * 判断数据库密码是否是 BCrypt 密文。
     *
     * @param password 数据库中保存的密码
     * @return true 表示是 BCrypt 密文，false 表示不是
     */
    private boolean isBCryptHash(String password) {
        return StringUtils.hasText(password)
                && (password.startsWith("$2a$")
                || password.startsWith("$2b$")
                || password.startsWith("$2y$"));
    }

    /**
     * 打印登录失败的具体后台原因。
     *
     * @param reason 失败原因
     * @param account 前端提交的账号
     * @param user 数据库查询到的用户，可能为空
     */
    private void printLoginError(String reason, String account, JobUser user) {
        System.err.println();
        System.err.println("========== Job-Agent 登录失败诊断 ==========");
        System.err.println("触发位置：com.job.bootstrap.service.impl.JobUserServiceImpl.login");
        System.err.println("失败原因：" + reason);
        System.err.println("前端账号：" + account);
        if (user != null) {
            System.err.println("数据库用户ID：" + user.getId());
            System.err.println("数据库用户名：" + user.getUsername());
            System.err.println("数据库密码格式：" + describePasswordFormat(user.getPassword()));
        }
        System.err.println("修复建议：如果数据库密码是明文，请重新注册用户，或把数据库 password 字段改成 BCrypt 密文。");
        System.err.println("==========================================");
        System.err.println();
    }

    /**
     * 描述密码格式，不打印完整密码，避免敏感信息泄露。
     *
     * @param password 数据库密码
     * @return 返回密码格式说明
     */
    private String describePasswordFormat(String password) {
        if (!StringUtils.hasText(password)) {
            return "空值";
        }
        if (isBCryptHash(password)) {
            return "BCrypt 密文，长度=" + password.length();
        }
        return "不是 BCrypt 密文，长度=" + password.length() + "，开头=" + password.substring(0, Math.min(password.length(), 3));
    }
}
