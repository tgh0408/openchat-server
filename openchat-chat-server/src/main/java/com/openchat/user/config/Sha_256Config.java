package com.openchat.user.config;

import com.openchat.common.utils.duplicateFiltering.StrTreeMap;
import com.openchat.user.dao.EmojiDao;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class Sha_256Config {
    public static final String SHA_256_FILTER = "SHA-256";
    final EmojiDao emojiDao;

    @Bean(SHA_256_FILTER)
    public StrTreeMap loadSha256Filter() {
        log.info("开始生成SHA-256表情包过滤");
        StrTreeMap filter = new StrTreeMap();
        List<String> words = emojiDao.listDigests()
                .stream()
                .filter(item -> item.getSha256() != null && !item.getSha256().isEmpty())
                .map(item -> {
                    filter.getFilterMap().putIfAbsent(item.getSha256(), item.getId());
                    return item.getSha256();
                })
                .toList();

        if (!words.isEmpty()) filter.loadWord(words);
        log.info("生成SHA-256表情包过滤成功. find files num: {}", words.size());
        return filter;
    }

}
