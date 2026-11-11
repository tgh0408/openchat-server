package com.openchat.common.factory;

import com.openchat.chat.dao.SensitiveWordDao;
import com.openchat.chat.domain.entity.SensitiveWord;
import com.openchat.common.utils.sensitiveWord.IWordFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class MyWordFactory implements IWordFactory {
    final SensitiveWordDao sensitiveWordDao;

    @Override
    public List<String> getWordList() {
        return sensitiveWordDao.list()
                .stream()
                .parallel()
                .map(SensitiveWord::getWord)
                .collect(Collectors.toList());
    }
}
