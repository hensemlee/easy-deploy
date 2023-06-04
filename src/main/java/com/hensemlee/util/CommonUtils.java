package com.hensemlee.util;

/**
 * @author hensemlee
 * @email lijun@tezign.com
 * @create 2023/6/4 12:17
 */
public class CommonUtils {
	public static String removeIllegalChars(String str) {
		// 替换 / : " < > | ? *  为 -
		return str.replaceAll("[/:\"><|?*]", "-");
	}
}
