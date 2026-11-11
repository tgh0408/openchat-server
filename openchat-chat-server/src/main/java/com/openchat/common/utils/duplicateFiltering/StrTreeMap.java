package com.openchat.common.utils.duplicateFiltering;

import lombok.Getter;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class StrTreeMap implements DuplicateFiltering{

    @Getter
    private final Map<String, Object> filterMap = new ConcurrentHashMap<>();

    private final Node node;

    public StrTreeMap() {
        this.node = new Node();
    }


    @Override
    public boolean hasDuplicate(String text) {
        return node.contains(text);
    }

    @Override
    public void loadWord(List<String> words) {
        words.forEach(node::add);
    }

    @Override
    public void addWord(String word) {
        node.add(word);
    }

    public void addWordMap(String word, Object value) {
        addWord(word);
        filterMap.putIfAbsent(word, value);
    }
}

class Node {
    Map<Character, Node> children = new ConcurrentHashMap<>();
    boolean isEnd = false; // 用于标识字符串的结束

    // 添加一个字符到当前 Node
    public void add(String str) {
        if (str.isEmpty()) {
            isEnd = true;
            return;
        }

        char firstChar = str.charAt(0);
        Node child = children.get(firstChar);
        if (child == null) {
            child = new Node();
            children.put(firstChar, child);
        }
        child.add(str.substring(1));
    }

    // 检查一个字符串是否在结构中
    public boolean contains(String str) {
        if (str.isEmpty()) {
            return isEnd;
        }

        char firstChar = str.charAt(0);
        Node child = children.get(firstChar);
        if (child == null) {
            return false;
        }
        return child.contains(str.substring(1));
    }

    @Override
    public String toString() {
        return children.toString() + (isEnd ? true : "");
    }
}
