package com.github.saikanya.languageinterface.api

/**
 * 搜索类接口的匹配模式。
 *
 * @author saika
 */
enum class SearchClassMatchMode {
    /** 仅短类名与请求 [name] 完全一致（忽略大小写）时返回 */
    STRICT,

    /** 短类名包含 [name]（忽略大小写），与历史行为一致 */
    FUZZY,
}
