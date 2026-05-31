package com.ck.custom.llmlearn.utils;

import com.alibaba.fastjson2.JSONObject;
import com.ck.custom.llmlearn.domain.ChatCompletionRequest;
import io.micrometer.common.util.StringUtils;

/**
 * @author changkong
 * @date 2026/4/12 22:45
 **/
public class ModelMessageUtils {

    /**
     * 转换请求体
     *
     * @return ChatCompletion
     */
    public static void convertModelCompletion(ChatCompletionRequest chatCompletionRequest) {
        return;
    }

    /**
     * 转换给前端返回消息内容
     *
     * @return JSONObject
     */
    public static JSONObject convertModelChatResponse(String id, String content) {
        JSONObject jsonObject = new JSONObject();
        if ( StringUtils.isNotBlank(id) && StringUtils.isNotBlank(content) ) {
            jsonObject.put("id", id);
            jsonObject.put("content", content);
            return jsonObject;
        }
        return jsonObject;
    }

}
