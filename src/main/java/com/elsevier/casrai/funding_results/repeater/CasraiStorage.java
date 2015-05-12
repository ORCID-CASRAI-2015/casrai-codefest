package com.elsevier.casrai.funding_results.repeater;

import java.util.List;
import java.util.Map;

/**
 * Storage for casrai data
 */
public interface CasraiStorage {


	String getResumptionToken(String url);
	void setResumptionToken(String url, String resumptionToken);

	List<Map<String,Object>> getAwards(String lastObjectId, int noItemsToReturn);
	void saveAwards(List<Map<String,Object>> awards);

	Map<String, Object> getStatus();
}
