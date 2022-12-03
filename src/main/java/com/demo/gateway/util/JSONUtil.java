package com.demo.gateway.util;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;

import java.util.*;

import static com.alibaba.fastjson.serializer.SerializerFeature.WriteNullStringAsEmpty;

/**
 * JSON 工具类
 *
 * @author tom
 * @since v
 */
public class JSONUtil {

    private JSONUtil() {
    }

    /**
     * sort a json object by key
     */
    public static String sortJsonObject(JSONObject jsonObject) {
        if (jsonObject == null) {
            return null;
        }
        List<String> keys = new ArrayList<>(jsonObject.keySet());
        keys.sort(Comparator.naturalOrder());
        SortedMap<String, Object> map = new TreeMap<>();
        for (String key : keys) {
            // if value is json object, sort it
            if (jsonObject.get(key) instanceof JSONObject) {
                Object o = jsonObject.get(key);
                JSONObject subJson = JSON.parseObject(o.toString());
                String value = sortJsonObject(subJson);
                map.put(key, value);
            } else {
                map.put(key, jsonObject.get(key));
            }
        }
        JSON.toJSONString(map, WriteNullStringAsEmpty);
        return JSON.toJSONString(map, WriteNullStringAsEmpty);
    }

}
