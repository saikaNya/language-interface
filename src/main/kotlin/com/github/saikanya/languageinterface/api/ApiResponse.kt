package com.github.saikanya.languageinterface.api

import com.google.gson.GsonBuilder
import io.netty.buffer.Unpooled
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.http.*

/**
 * 统一 API 响应格式
 *
 * 响应结构: {"code": 0, "msg": "", "data": {}}
 *
 * @author saika
 */
object ApiResponse {

    /** 成功 */
    const val SUCCESS = 0

    /** 请求体不是合法的 JSON */
    const val INVALID_JSON = 1001

    /** 缺少必要参数 */
    const val MISSING_PARAM = 1002

    /** 未找到已打开的项目（IDE 中没有任何非默认工程） */
    const val PROJECT_NOT_FOUND = 2001

    /** 类或文件未找到 */
    const val CLASS_NOT_FOUND = 2002

    /** 请求中指定的 project 在已打开工程中不存在（名称或路径不匹配） */
    const val SPECIFIED_PROJECT_NOT_FOUND = 2003

    /** 索引正在构建中 */
    const val INDEXING = 3001

    private val gson = GsonBuilder().disableHtmlEscaping().create()

    fun success(ctx: ChannelHandlerContext, data: Any? = null) {
        send(ctx, SUCCESS, "", data)
    }

    fun error(ctx: ChannelHandlerContext, code: Int, msg: String) {
        send(ctx, code, msg, null)
    }

    private fun send(ctx: ChannelHandlerContext, code: Int, msg: String, data: Any?) {
        val body = linkedMapOf<String, Any?>(
            "code" to code,
            "msg" to msg,
            "data" to data,
        )
        val json = gson.toJson(body)
        val bytes = json.toByteArray(Charsets.UTF_8)
        val response = DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK, Unpooled.wrappedBuffer(bytes))
        response.headers().set(HttpHeaderNames.CONTENT_TYPE, "application/json; charset=utf-8")
        response.headers().setInt(HttpHeaderNames.CONTENT_LENGTH, bytes.size)
        response.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.CLOSE)
        ctx.channel().writeAndFlush(response)
    }
}
