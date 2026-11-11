package com.openchat.common.utils.discover;

import com.openchat.common.utils.discover.domain.UrlInfo;
import org.jsoup.nodes.Document;
import org.springframework.lang.Nullable;

import java.util.Map;

public interface UrlDiscover {
    @Nullable
    Map<String, UrlInfo> getUrlContentMap(String content);

    @Nullable
    UrlInfo getContent(String url);

    @Nullable
    String getTitle(Document document);

    @Nullable
    String getDescription(Document document);

    @Nullable
    String getImage(String url,Document document);
}
