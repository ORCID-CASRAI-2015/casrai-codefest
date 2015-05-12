package com.elsevier.casrai.funding_results.repeater;

import java.io.InputStreamReader;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.apache.commons.lang3.StringUtils;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DecompressingHttpClient;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.conn.PoolingClientConnectionManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.Lists;
import com.google.gson.Gson;

/**
 * Harvest casrai data from multiple endpoints
 */
public class CasraiHarvester {
	private static final Logger log = LoggerFactory.getLogger(CasraiHarvester.class);

	private List<String> harvestUrls;
	private CasraiStorage storage;
	private String filter;

	private long sleepTime = TimeUnit.SECONDS.toMillis(2);
	private HttpClient httpClient;

	// ------------------------------------------- Public Methods -------------------------------------------

	public void start() {
		this.httpClient = new DecompressingHttpClient(new DefaultHttpClient(new PoolingClientConnectionManager()));
		new Thread(this::harvest, getClass().getSimpleName()).start();
	}

	// ------------------------------------------- Private parts -------------------------------------------

	private void harvest() {
		while(true) {
			for (String url : harvestUrls) {
				try {
					harvest(url);
				} catch (Throwable ex) {
					log.error("Got uncaught exception during harvest of " + url, ex);
				}
			}
			try { Thread.sleep(sleepTime); } catch(Exception ex) {}
		}
	}

	@SuppressWarnings("unchecked")
	private void harvest(String url) {
		log.debug("Harvesting {}...", url);

		try {
			final FundingDataResponse data = loadData(url);
			if(filter != null) {
				data.getAwards().removeIf(a -> {
					final Map<String, Object> awardData = (Map<String, Object>) a.get("Funding Award");
					final String amountString = (String) awardData.get("Amount");
					final double amount = Double.parseDouble(StringUtils.substringBefore(amountString, " "));
					final boolean even = ((int) amount) % 2 == 0;
					if("even".equals(filter))
						return !even;
					else
						return even;
				});
			}
			if(data.getAwards().size() > 0) {
				log.info("Got {} awards from {}", data.getAwards().size(), url);
				storage.saveAwards(data.getAwards());
			}
			storage.setResumptionToken(url, data.getResumptionToken());
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	@SuppressWarnings("unchecked")
	private FundingDataResponse loadData(String url) throws Exception {

		String fullUri = url;
		final String resumptionToken = storage.getResumptionToken(url);
		if(resumptionToken != null)
			fullUri += "?resumptionToken=" + URLEncoder.encode(resumptionToken, "utf-8");

		final HttpGet httpGet = new HttpGet(fullUri);
		final FundingDataResponse ret = new FundingDataResponse();
		httpClient.execute(httpGet, response -> {
			final InputStreamReader reader = new InputStreamReader(response.getEntity().getContent(), StandardCharsets.UTF_8);
			final Map<String, Object> map = new Gson().fromJson(reader, Map.class);
			try {
				final List<Map<String, Object>> awards = (List<Map<String, Object>>) map.get("awards");
				for (Map<String, Object> award : awards) {
					CasraiUtils.translateInput(award);
					ret.getAwards().add(award);
				}
				ret.setResumptionToken((String) map.get("resumptionToken"));
			} catch (ParseException e) {
				throw new RuntimeException(e);
			}
			return null;
		});
		return ret;
	}


	private static class FundingDataResponse {
		private final List<Map<String, Object>> awards = Lists.newArrayList();
		private String resumptionToken;

		public List<Map<String, Object>> getAwards() {
			return awards;
		}

		public String getResumptionToken() {
			return resumptionToken;
		}

		public void setResumptionToken(String resumptionToken) {
			this.resumptionToken = resumptionToken;
		}
	}

	// ------------------------------------------- Getters/Setters -------------------------------------------

	public void setHarvestUrls(List<String> harvestUrls) {
		this.harvestUrls = harvestUrls;
	}

	public void setStorage(CasraiStorage storage) {
		this.storage = storage;
	}

	public void setFilter(String filter) {
		this.filter = filter;
	}
}
