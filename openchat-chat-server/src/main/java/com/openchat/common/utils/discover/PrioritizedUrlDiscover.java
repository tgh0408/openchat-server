package com.openchat.common.utils.discover;

import cn.hutool.core.util.StrUtil;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.Nullable;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Description: 具有优先级的title查询器
 * Author: <a href="https://github.com/zongzibinbin">abin</a>
 * Date: 2023-05-27
 */

//此类仅仅为多个责任链的汇总
@Component
@RequiredArgsConstructor
public class PrioritizedUrlDiscover extends AbstractUrlDiscover {

    final List<UrlDiscover> urlDiscovers;

    @Nullable
    @Override
    public String getTitle(Document document) {
        for (UrlDiscover urlDiscover : urlDiscovers) {
            String urlTitle = urlDiscover.getTitle(document);
            if (StrUtil.isNotBlank(urlTitle)) {
                return urlTitle;
            }
        }
        return null;
    }

    @Nullable
    @Override
    public String getDescription(Document document) {
        for (UrlDiscover urlDiscover : urlDiscovers) {
            String urlDescription = urlDiscover.getDescription(document);
            if (StrUtil.isNotBlank(urlDescription)) {
                return urlDescription;
            }
        }
        return null;
    }

    @Nullable
    @Override
    public String getImage(String url, Document document) {
        for (UrlDiscover urlDiscover : urlDiscovers) {
            String urlImage = urlDiscover.getImage(url, document);
            if (StrUtil.isNotBlank(urlImage)) {
                return urlImage;
            }
        }
        return null;
    }
}
