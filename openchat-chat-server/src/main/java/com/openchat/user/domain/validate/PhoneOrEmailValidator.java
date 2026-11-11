package com.openchat.user.domain.validate;

import com.openchat.user.domain.enums.IdTypeEnum;
import com.openchat.user.domain.validate.interfaces.PhoneOrEmail;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.util.regex.Pattern;

public class PhoneOrEmailValidator implements ConstraintValidator<PhoneOrEmail, String> {

    public static final String PHONE_REGEX = "^1[3-9]\\d{9}$";
    public static final String EMAIL_REGEX = "^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$";
    public static final String NUMBER_REGEX = "^\\d+$";

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        return switch (getIdType(value)) {
            case PHONE, EMAIL -> true;
            default -> false;
        };
    }

    /**
     * 按手机号、邮箱、纯数字 ID 的顺序识别类型；空值或不匹配时返回 UNKNOWN，不裁剪空白。
     */
    public static IdTypeEnum getIdType(String value) {
        if (value == null) {
            return IdTypeEnum.UNKNOWN;
        }
        if (Pattern.matches(PHONE_REGEX, value)) {
            return IdTypeEnum.PHONE;
        }
        if (Pattern.matches(EMAIL_REGEX, value)) {
            return IdTypeEnum.EMAIL;
        }
        if (Pattern.matches(NUMBER_REGEX, value)) {
            return IdTypeEnum.ID;
        }
        return IdTypeEnum.UNKNOWN;
    }
}
