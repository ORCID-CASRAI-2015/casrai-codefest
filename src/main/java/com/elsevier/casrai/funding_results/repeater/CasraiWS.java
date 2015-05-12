package com.elsevier.casrai.funding_results.repeater;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.Maps;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import io.undertow.Undertow;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.PathTemplateHandler;
import io.undertow.util.Headers;

/**
 * Exposes casrai data via a rest/json endpoint
 */
public class CasraiWS {
	private static final Logger log = LoggerFactory.getLogger(CasraiWS.class);

	private int port;
	private CasraiStorage storage;
	private String filter;

	// ------------------------------------------- Public Methods -------------------------------------------

	public void start() {
		final PathTemplateHandler pathTemplateHandler = new PathTemplateHandler();
		pathTemplateHandler.add("/fundingResults", new SimpleHandler(this::fundingResults));
		pathTemplateHandler.add("/status", new SimpleHandler(this::status));

		// Build server
		Undertow server = Undertow.builder()
				.addHttpListener(port, "0.0.0.0")
				.setHandler(pathTemplateHandler)
				.build();

		// And start
		server.start();
	}

	// ------------------------------------------- Private parts -------------------------------------------

	private void status(Map<String, Deque<String>> request, Map<String, Object> response) {
		response.putAll(storage.getStatus());
		if(filter != null)
			response.put("filter", filter);
	}

	private void fundingResults(Map<String, Deque<String>> request, Map<String, Object> response) {
		String lastObjectId = request.getOrDefault("resumptionToken", new ArrayDeque<>()).peekFirst();
		final List<Map<String, Object>> awards = storage.getAwards(lastObjectId, 10);
		response.put("awards", awards);
		if (awards.size() > 0)
			lastObjectId = awards.get(awards.size() - 1).get("_id").toString();
		for (Map<String, Object> award : awards) {
			award.remove("_id");
			award.remove("_type");
		}
		response.put("resumptionToken", lastObjectId);
	}

	/**
	 * A http handler that wraps a method returning pure json. Used for
	 * simplicity.
	 */
	private static class SimpleHandler implements HttpHandler {

		private final BiConsumer<Map<String, Deque<String>>, Map<String, Object>> consumer;

		public SimpleHandler(BiConsumer<Map<String, Deque<String>>, Map<String, Object>> consumer) {
			this.consumer = consumer;
		}

		@Override
		public void handleRequest(HttpServerExchange exchange) throws Exception {
			final Map<String, Deque<String>> queryParameters = exchange.getQueryParameters();
			final Map<String, Object> result = Maps.newLinkedHashMap();
			consumer.accept(queryParameters, result);

			if(result.containsKey("returnCode"))
				exchange.setResponseCode((Integer) result.get("returnCode"));
			exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "application/json");

			final Gson gson = new GsonBuilder()
					.setPrettyPrinting()
					.disableHtmlEscaping()
					.setDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'")
					.create();
			exchange.getResponseSender().send(gson.toJson(result));
		}
	}


	// ------------------------------------------- Getters/Setters -------------------------------------------

	public void setPort(int port) {
		this.port = port;
	}

	public void setStorage(CasraiStorage storage) {
		this.storage = storage;
	}

	public void setFilter(String filter) {
		this.filter = filter;
	}
}
