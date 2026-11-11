package com.openchat.user.service.adapter;

import cn.hutool.extra.pinyin.engine.pinyin4j.Pinyin4jEngine;
import com.openchat.common.exception.BusinessException;

import java.text.Normalizer;
import java.util.Locale;

/** 排序规则 v1：固定引擎、无声调、全角归一化；0 为 A-Z，1 为其他字符。 */
public final class NameSortKey {
    public static final int MAX_LENGTH = 1024;
    public static final String EMPTY = "1:";

    private NameSortKey() {
    }

    public static String build(String name) {
        if (name == null) {
            return null;
        }
        String normalized = Normalizer.normalize(name, Normalizer.Form.NFKC).strip();
        if (normalized.isEmpty()) {
            return null;
        }
        // 每次使用独立格式对象，不依赖引擎内部可变格式的跨线程安全性。
        String pinyin = new Pinyin4jEngine().getPinyin(normalized, "").toLowerCase(Locale.ROOT);
        char first = pinyin.charAt(0);
        String key = (first >= 'a' && first <= 'z' ? "0:" : "1:") + pinyin;
        if (!isValid(key)) {
            throw new BusinessException("名称过长或包含无效字符，无法生成排序键");
        }
        return key;
    }

    public static String indexLetter(String key) {
        if (key != null && key.startsWith("0:") && key.length() > 2) {
            return String.valueOf(Character.toUpperCase(key.charAt(2)));
        }
        return "#";
    }

    public static boolean isValid(String key) {
        if (key == null || key.length() > MAX_LENGTH) {
            return false;
        }
        boolean letters = key.startsWith("0:") && key.length() > 2
                && key.charAt(2) >= 'a' && key.charAt(2) <= 'z';
        if (!letters && !key.startsWith("1:")) {
            return false;
        }
        return key.codePoints().noneMatch(code -> code >= Character.MIN_SURROGATE
                && code <= Character.MAX_SURROGATE);
    }
}
