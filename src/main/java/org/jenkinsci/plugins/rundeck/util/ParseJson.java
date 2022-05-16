package org.jenkinsci.plugins.rundeck.util;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import org.jsoup.Jsoup;
import org.jsoup.safety.Safelist;

import java.util.Map;

public class ParseJson {

    public static JsonElement clean(JsonElement elem) {
        if (elem.isJsonPrimitive()) {
            JsonPrimitive primitive = elem.getAsJsonPrimitive();
            if(primitive.isString()) {
                String cleaned = Jsoup.clean(primitive.getAsString(), Safelist.none());
                return new JsonPrimitive(cleaned);
            } else {
                return primitive;
            }
        } else if (elem.isJsonArray()) {
            JsonArray cleanArray = new JsonArray();
            for(JsonElement arrayElement: elem.getAsJsonArray()) {
                cleanArray.add(clean(arrayElement));
            }
            return cleanArray;
        } else {
            JsonObject obj = elem.getAsJsonObject();
            JsonObject clean = new JsonObject();
            for(Map.Entry<String, JsonElement> entry :  obj.entrySet()) {
                clean.add(Jsoup.clean(entry.getKey(), Safelist.none()), clean(entry.getValue()));
            }
            return clean;
        }
    }

}
