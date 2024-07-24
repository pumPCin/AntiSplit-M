/*
 * Copyright (c) 2002 JSON.org (now "Public Domain")
 * This is NOT property of REAndroid
 * This package is renamed from org.json.* to avoid class conflict when used on anroid platforms
*/
package com.reandroid.json;

import android.os.Build;

import java.util.Base64;

public class JsonUtil {

    public static byte[] parseBase64(String text){
        if(text == null || !text.startsWith(JSONItem.MIME_BIN_BASE64)){
            return null;
        }
        text = text.substring(JSONItem.MIME_BIN_BASE64.length());
        try{
            return (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) ? Base64.getUrlDecoder().decode(text) : android.util.Base64.decode(text, android.util.Base64.URL_SAFE);
        }catch (Throwable throwable){
            throw new JSONException(throwable);
        }
    }
}
