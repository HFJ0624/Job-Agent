package com.job.exception;

import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.exception.SaTokenException;
import com.job.common.entity.base.Result;
import com.job.common.entity.base.ResultCodeEnum;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import lombok.RequiredArgsConstructor;
import org.springframework.core.env.Environment;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

/**
 * 作者:hfj
 * 功能:全局异常处理器，将 Controller 抛出的异常统一转换成 Result 响应
 * 日期:2026/6/2 10:45
 */
@Slf4j
@RequiredArgsConstructor
@RestControllerAdvice(basePackages = "com.job")
public class GlobalExceptionHandler {

    private final Environment environment;

    /**
     * 处理业务异常。
     *
     * @param exception 业务异常对象
     * @param request 当前请求对象
     * @return 返回业务异常对应的统一响应
     */
    @ExceptionHandler(BizException.class)
    public Result<Object> handleBizException(BizException exception, HttpServletRequest request) {
        printError("业务异常", exception.getCode(), exception.getMessage(), exception, request, true);
        return Result.build(null, exception.getCode(), exception.getMessage());
    }

    /**
     * 处理请求体参数校验异常。
     * P表示参数描述，主要对应 @RequestBody 参数上的校验失败。
     *
     * @param exception 参数校验异常对象
     * @param request 当前请求对象
     * @return 返回参数错误响应
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<Object> handleMethodArgumentNotValidException(MethodArgumentNotValidException exception,
                                                                 HttpServletRequest request) {
        // 1. 将多个字段错误拼接成一段中文提示，方便前端直接展示。
        String message = exception.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(this::formatFieldError)
                .collect(Collectors.joining("；"));
        printError("请求体参数校验异常", ResultCodeEnum.PARAM_ERROR.getCode(), message, exception, request, false);
        return Result.build(null, ResultCodeEnum.PARAM_ERROR.getCode(), message);
    }

    /**
     * 处理表单参数绑定校验异常。
     * P表示参数描述，主要对应 multipart/form-data 里的 @ModelAttribute DTO 校验失败。
     *
     * @param exception 表单绑定异常对象
     * @param request 当前请求对象
     * @return 返回参数错误响应
     */
    @ExceptionHandler(BindException.class)
    public Result<Object> handleBindException(BindException exception, HttpServletRequest request) {
        // 1. multipart 表单没有 JSON 请求体，校验失败时会进入这里。
        String message = exception.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(this::formatFieldError)
                .collect(Collectors.joining("；"));
        printError("表单参数校验异常", ResultCodeEnum.PARAM_ERROR.getCode(), message, exception, request, false);
        return Result.build(null, ResultCodeEnum.PARAM_ERROR.getCode(), message);
    }

    /**
     * 处理普通参数校验异常。
     *
     * @param exception 参数校验异常对象
     * @param request 当前请求对象
     * @return 返回参数错误响应
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public Result<Object> handleConstraintViolationException(ConstraintViolationException exception,
                                                             HttpServletRequest request) {
        // 1. 这里主要处理 GET 请求参数上的校验失败。
        String message = exception.getConstraintViolations()
                .stream()
                .map(ConstraintViolation::getMessage)
                .collect(Collectors.joining("；"));
        printError("请求参数校验异常", ResultCodeEnum.PARAM_ERROR.getCode(), message, exception, request, false);
        return Result.build(null, ResultCodeEnum.PARAM_ERROR.getCode(), message);
    }

    /**
     * 处理 Sa-Token 登录认证异常。
     *
     * @param exception 登录认证异常对象
     * @param request 当前请求对象
     * @return 返回请先登录响应
     */
    @ExceptionHandler({NotLoginException.class, SaTokenException.class})
    public Result<Object> handleSaTokenException(Exception exception, HttpServletRequest request) {
        // 1. 认证失败只记录简短日志，不把内部异常细节返回给前端。
        printError("登录认证异常", ResultCodeEnum.LOGIN_AUTH.getCode(), exception.getMessage(), exception, request, false);
        log.warn("Sa-Token authentication failed: {}", exception.getMessage());
        return Result.build(null, ResultCodeEnum.LOGIN_AUTH.getCode(), "请先登录");
    }

    /**
     * 处理系统兜底异常。
     *
     * @param exception 未被前面规则捕获的异常
     * @param request 当前请求对象
     * @return 返回系统异常响应
     */
    @ExceptionHandler(Exception.class)
    public Result<Object> handleException(Exception exception, HttpServletRequest request) {
        // 1. 兜底异常需要打印完整日志，方便后端排查问题。
        printError("系统异常", ResultCodeEnum.SYSTEM_ERROR.getCode(), exception.getMessage(), exception, request, true);
        log.error("System exception", exception);
        return Result.build(buildDebugMessage(exception), ResultCodeEnum.SYSTEM_ERROR.getCode(), ResultCodeEnum.SYSTEM_ERROR.getMessage());
    }

    /**
     * 格式化字段校验错误。
     *
     * @param fieldError 单个字段错误
     * @return 返回“字段名：错误信息”格式的文本
     */
    private String formatFieldError(FieldError fieldError) {
        return fieldError.getField() + "：" + fieldError.getDefaultMessage();
    }

    /**
     * 打印接口异常到后台控制台。
     *
     * @param title 错误标题
     * @param code 业务状态码
     * @param message 错误信息
     * @param exception 异常对象
     * @param request 当前请求对象
     * @param printStack 是否打印完整堆栈
     */
    private void printError(String title,
                            Integer code,
                            String message,
                            Exception exception,
                            HttpServletRequest request,
                            boolean printStack) {
        System.err.println();
        System.err.println("========== Job-Agent " + title + " ==========");
        System.err.println("请求方法：" + request.getMethod());
        System.err.println("请求地址：" + buildRequestUrl(request));
        System.err.println("错误码：" + code);
        System.err.println("异常类型：" + exception.getClass().getName());
        System.err.println("错误信息：" + message);
        System.err.println("触发位置：" + findProjectStackTrace(exception));
        if (printStack) {
            System.err.println("完整堆栈：");
            exception.printStackTrace(System.err);
        }
        System.err.println("========================================");
        System.err.println();
    }

    /**
     * 拼接当前请求地址。
     *
     * @param request 当前请求对象
     * @return 返回包含 queryString 的请求地址
     */
    private String buildRequestUrl(HttpServletRequest request) {
        String queryString = request.getQueryString();
        if (queryString == null) {
            return request.getRequestURI();
        }
        return request.getRequestURI() + "?" + queryString;
    }

    /**
     * 查找项目代码中的异常触发位置。
     *
     * @param exception 异常对象
     * @return 返回最靠前的 com.job 包堆栈位置
     */
    private String findProjectStackTrace(Exception exception) {
        for (StackTraceElement element : exception.getStackTrace()) {
            if (element.getClassName().startsWith("com.job")) {
                return element.toString();
            }
        }
        return "未找到项目代码位置，请查看完整堆栈";
    }

    /**
     * 生成调试信息。
     *
     * @param exception 异常对象
     * @return 非生产环境返回异常摘要，生产环境不返回内部细节
     */
    private String buildDebugMessage(Exception exception) {
        if (environment.matchesProfiles("prod")) {
            return null;
        }
        return exception.getClass().getName() + "：" + exception.getMessage();
    }
}
