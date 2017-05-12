package org.thomaspengo.tslim.util;

import java.util.Map;
import java.util.TreeMap;

public class Utils {
	public static Map<String,String> parseParameters(String parameters) {
		// Extract key-value pairs from the arguments
		Map<String,String> map = new TreeMap<String,String>();
		java.util.regex.Matcher m = java.util.regex.Pattern.compile("(\\w)+=((\\w)+|[\\[].*[\\]])").matcher(parameters);
		while(m.find()) {
			String s = m.group();
			String key = s.split("=")[0];
			String value = s.split("=")[1];
			if (value.length()>0 && value.charAt(0)=='[') {
				value = value.substring(1, value.length()-1);
			}
			map.put(key,value);
		}
		
		return map;
	}
}
