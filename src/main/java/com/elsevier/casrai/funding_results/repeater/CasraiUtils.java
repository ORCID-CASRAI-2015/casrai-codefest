package com.elsevier.casrai.funding_results.repeater;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Map;

import com.google.common.collect.Lists;

/**
 * Misc utility methods
 */
public class CasraiUtils {

	private static final List<String[]> DATE_PATHS = Lists.newArrayList(
			new String[] {"Funding Award", "Start Date"}
			, new String[] {"Funding Award", "End Date"}
	);
	// ISO 8601: "2015-05-01T09:18:05Z"
	private static final SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");

	public static void translateInput(Map<String, Object> data) throws ParseException {
		for (String[] path : DATE_PATHS) {
			translateInput(data, path, 0);
		}
	}
	@SuppressWarnings("unchecked")
	private static void translateInput(Map<String, Object> data, String[] path, int idx) throws ParseException {
		final Object value = data.get(path[idx]);
		if(value == null)
			return;
		if(idx == path.length - 1) {
			// we are at the end
			data.put(path[idx], df.parse((String) value));
		} else {
			// we need to go deeper
			translateInput((Map<String, Object>) value, path, idx+1);
		}
	}

}
