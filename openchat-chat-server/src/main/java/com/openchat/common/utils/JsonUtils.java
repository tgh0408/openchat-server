package com.openchat.common.utils;

import lombok.Getter;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

/**
 * JSON conversion helpers used by cache and SSE payloads.
 */
public class JsonUtils {

    @Getter
    private static final JsonMapper jsonMapper = SpringUtils.getBean(JsonMapper.class);

    public static <T> T toObj(String str, Class<T> clz) {
        try {
            return jsonMapper.readValue(str, clz);
        } catch (JacksonException e) {
            throw new UnsupportedOperationException(e);
        }
    }

    public static <T> T toObj(String str, TypeReference<T> clz) {
        try {
            return jsonMapper.readValue(str, clz);
        } catch (JacksonException e) {
            throw new UnsupportedOperationException(e);
        }
    }

    public static <T> List<T> toList(String str, Class<T> clz) {
        try {
            return jsonMapper.readValue(str, new TypeReference<List<T>>() {
            });
        } catch (JacksonException e) {
            throw new UnsupportedOperationException(e);
        }
    }

    public static JsonNode toJsonNode(String str) {
        try {
            return jsonMapper.readTree(str);
        } catch (JacksonException e) {
            throw new UnsupportedOperationException(e);
        }
    }

    public static <T> T nodeToValue(JsonNode node, Class<T> clz) {
        try {
            return jsonMapper.treeToValue(node, clz);
        } catch (JacksonException e) {
            throw new UnsupportedOperationException(e);
        }
    }

    public static String toStr(Object value) {
        try {
            return jsonMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new UnsupportedOperationException(e);
        }
    }
}
