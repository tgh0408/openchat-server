package com.openchat.common.utils.sensitiveWord;

import com.openchat.common.algorithm.ac.ACTrie;
import com.openchat.common.algorithm.ac.MatchResult;
import lombok.Getter;
import org.HdrHistogram.ConcurrentHistogram;
import org.apache.commons.lang3.StringUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 基于ac自动机实现的敏感词过滤工具类
 * 可以用来替代{@link ConcurrentHistogram}
 * 为了兼容提供了相同的api接口 {@code hasSensitiveWord}
 * <p>
 * Created by berg on 2023/6/18.
 */
@Getter
public class ACFilter implements SensitiveWordFilter {

    private ACFilter(){}

    public static ACFilter getInstance(){
        return new ACFilter();
    }

    private final static char mask_char = '*'; // 替代字符

    //拓展内容,可做映射
    final Map<String, Object> filterMap = new HashMap<>();

    private static ACTrie ac_trie = null;

    /**
     * 有敏感词
     *
     * @param text 文本
     * @return boolean
     */
    public boolean hasSensitiveWord(String text) {
        if (StringUtils.isBlank(text)) return false;
        return !Objects.equals(filter(text), text);
    }

    /**
     * 敏感词替换
     *
     * @param text 待替换文本
     * @return 替换后的文本
     */
    public String filter(String text) {
        if (StringUtils.isBlank(text)) return text;
        List<MatchResult> matchResults = ac_trie.matches(text);
        StringBuffer result = new StringBuffer(text);
        // matchResults是按照startIndex排序的，因此可以通过不断更新endIndex最大值的方式算出尚未被替代部分
        int endIndex = 0;
        for (MatchResult matchResult : matchResults) {
            endIndex = Math.max(endIndex, matchResult.getEndIndex());
            replaceBetween(result, matchResult.getStartIndex(), endIndex);
        }
        return result.toString();
    }

    private static void replaceBetween(StringBuffer buffer, int startIndex, int endIndex) {
        for (int i = startIndex; i < endIndex; i++) {
            buffer.setCharAt(i, mask_char);
        }
    }

    /**
     * 加载敏感词列表
     *
     * @param words 敏感词数组
     */
    public void loadWord(List<String> words) {
        if (words == null) return;
        ac_trie = new ACTrie(words);
    }

    /**
     * @param word 增加一个节点
     */
    public void addWord(String word) {
        if (word == null) return;
        ac_trie.addWord(word);
    }

    /**
     * @param word 增加一个节点
     * @param value 节点映射
     */
    public void addWordMap(String word,Object value) {
        addWord(word);
        filterMap.put(word, value);
    }
}
