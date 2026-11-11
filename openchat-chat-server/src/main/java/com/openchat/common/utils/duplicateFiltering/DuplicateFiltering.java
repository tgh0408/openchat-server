package com.openchat.common.utils.duplicateFiltering;


import java.util.List;

/**
 * 敏感词过滤
 */
public interface DuplicateFiltering {
    /**
     * 是否重复
     *
     * @param text 文本
     * @return boolean
     */
    boolean hasDuplicate(String text);


    /**
     * 加载敏感过滤列表
     *
     * @param words 过滤列表
     */
    void loadWord(List<String> words);


    /**
     * 新增过滤项
     * @param word 新增文本
     */
    void addWord(String word);


}
