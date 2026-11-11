package com.openchat.common.utils;

import cn.hutool.core.util.ObjectUtil;
import com.openchat.common.exception.BusinessException;
import com.openchat.common.exception.CommonErrorEnum;
import com.openchat.common.exception.ErrorEnum;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.hibernate.validator.HibernateValidator;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.text.MessageFormat;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 校验工具类
 */
public class AssertUtil {

    /**
     * 校验到失败就结束
     */
    private static final Validator failFastValidator = Validation.byProvider(HibernateValidator.class)
            .configure()
            .failFast(true)
            .buildValidatorFactory()
            .getValidator();

    /**
     * 全部校验
     */
    private static final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
    private static final Log log = LogFactory.getLog(AssertUtil.class);

    /**
     * 注解验证参数(校验到失败就结束)
     *
     * @param obj
     */
    public static <T> void fastFailValidate(T obj) {
        Set<ConstraintViolation<T>> constraintViolations = failFastValidator.validate(obj);
        if (!constraintViolations.isEmpty()) {
            throwException(CommonErrorEnum.PARAM_INVALID, constraintViolations.iterator().next().getMessage());
        }
    }

    /**
     * 注解验证参数(全部校验,抛出异常)
     */
    //模仿spring的参数校验如@NotNull @NotBlank @NotEmpty @Size @Pattern @Email @Length @Min @Max
    public static <T> void allCheckValidateThrow(T obj) {
        Set<ConstraintViolation<T>> constraintViolations = validator.validate(obj);
        if (!constraintViolations.isEmpty()) {
            StringBuilder errorMsg = new StringBuilder();
            for (ConstraintViolation<T> violation : constraintViolations) {
                //拼接异常信息
                errorMsg.append(violation.getPropertyPath().toString()).append(":").append(violation.getMessage()).append(",");
            }
            //去掉最后一个逗号
            throw new BusinessException(errorMsg.substring(0, errorMsg.length() - 1));
        }
    }


    /**
     * 注解验证参数(全部校验,返回异常信息集合)
     *
     * @param obj
     */
    public static <T> Map<String, String> allCheckValidate(T obj) {
        Set<ConstraintViolation<T>> constraintViolations = validator.validate(obj);
        if (!constraintViolations.isEmpty()) {
            Map<String, String> errorMessages = new HashMap<>();
            for (ConstraintViolation<T> violation : constraintViolations) {
                errorMessages.put(violation.getPropertyPath().toString(), violation.getMessage());
            }
            return errorMessages;
        }
        return new HashMap<>();
    }

    //如果不是true，则抛异常
    public static void isTrue(boolean expression, String msg) {
        if (!expression) {
            throwException(msg);
        }
    }

    public static void isTrue(boolean expression, ErrorEnum errorEnum, Object... args) {
        if (!expression) {
            throwException(errorEnum, args);
        }
    }

    //如果是true，则抛异常
    public static void isFalse(boolean expression, String msg) {
        if (expression) {
            throwException(msg);
        }
    }

    //如果是true，则抛异常
    public static void isFalse(boolean expression, ErrorEnum errorEnum, Object... args) {
        if (expression) {
            throwException(errorEnum, args);
        }
    }

    //如果不是非空对象，则抛异常
    public static void isNotEmpty(Object obj, String msg) {
        if (isEmpty(obj)) {
            throwException(msg);
        }
    }

    //如果不是非空对象，则抛异常
    public static void isNotEmpty(Object obj, ErrorEnum errorEnum, Object... args) {
        if (isEmpty(obj)) {
            throwException(errorEnum, args);
        }
    }

    //如果不是非空对象，则抛异常
    public static void isEmpty(Object obj, String msg) {
        if (!isEmpty(obj)) {
            throwException(msg);
        }
    }

    /**
     * 必须为 null，否则抛出业务异常。
     */
    public static void isNull(Object obj, String msg) {
        if (Objects.nonNull(obj)) {
            throwException(msg);
        }
    }

    /**
     * 必须为 null，否则按指定错误枚举抛出业务异常。
     */
    public static void isNull(Object obj, ErrorEnum errorEnum, Object... args) {
        if (Objects.nonNull(obj)) {
            throwException(errorEnum, args);
        }
    }

    /**
     * 必须为非 null，否则抛出业务异常。
     */
    public static void isNonNull(Object obj, String msg) {
        if (Objects.isNull(obj)) {
            throwException(msg);
        }
    }

    /**
     * 必须为非 null，否则抛出业务异常。
     */
    public static void isNonNull(Object obj, String key, String msg) {
        if (Objects.isNull(obj)) {
            log.error(msg + ":" + key);
            throwException(msg);
        }
    }

    /**
     * 必须为非 null，否则按指定错误枚举抛出业务异常。
     */
    public static void isNonNull(Object obj, ErrorEnum errorEnum, Object... args) {
        if (Objects.isNull(obj)) {
            throwException(errorEnum, args);
        }
    }

    public static void equal(Object o1, Object o2, String msg) {
        if (!ObjectUtil.equal(o1, o2)) {
            throwException(msg);
        }
    }

    public static void notEqual(Object o1, Object o2, String msg) {
        if (ObjectUtil.equal(o1, o2)) {
            throwException(msg);
        }
    }

    private static boolean isEmpty(Object obj) {
        return ObjectUtil.isEmpty(obj);
    }

    private static void throwException(String msg) {
        throwException(null, msg);
    }

    private static void throwException(ErrorEnum errorEnum, Object... arg) {
        if (Objects.isNull(errorEnum)) {
            errorEnum = CommonErrorEnum.BUSINESS_ERROR;
        }
        throw new BusinessException(errorEnum.getErrorCode(), MessageFormat.format(errorEnum.getErrorMsg(), arg));
    }


}
