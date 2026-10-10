package com.lekha.transactions;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Talks to a model served by Ollama on this computer, through its REST API. {@code /api/chat} takes the conversation
 * and a JSON schema in {@code format}, and Ollama makes the model's reply follow that schema.
 */
@Component
class OllamaModel implements LanguageModel {

	private static final JsonMapper JSON = JsonMapper.builder().build();

	private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(2);

	/** How long Ollama keeps the model in memory after a request, so the next batch doesn't reload it. */
	private static final String KEEP_ALIVE = "10m";

	private final RestClient http;

	private final String baseUrl;

	private final String model;

	@Autowired
	OllamaModel(@Value("${lekha.ai.base-url}") String baseUrl, @Value("${lekha.ai.model}") String model,
			@Value("${lekha.ai.timeout}") Duration timeout) {
		this(RestClient.builder().requestFactory(requestFactory(timeout)), baseUrl, model);
	}

	OllamaModel(RestClient.Builder http, String baseUrl, String model) {
		this.http = http.baseUrl(baseUrl).build();
		this.baseUrl = baseUrl;
		this.model = model;
	}

	@Override
	public String name() {
		return model;
	}

	/** Asks Ollama which models it has downloaded. A model named without a tag is stored as {@code name:latest}. */
	@Override
	public Status status() {
		JsonNode tags;
		try {
			tags = JSON.readTree(http.get().uri("/api/tags").retrieve().body(String.class));
		}
		catch (ResourceAccessException ex) {
			return new Status(false, notRunning());
		}
		catch (RestClientResponseException | JacksonException ex) {
			return new Status(false, "Ollama at " + baseUrl + " gave an unexpected answer. Try restarting it.");
		}
		for (JsonNode installed : tags.path("models")) {
			String name = installed.path("name").asString("");
			if (name.equals(model) || name.equals(model + ":latest")) {
				return new Status(true, null);
			}
		}
		return new Status(false, notDownloaded());
	}

	@Override
	public String chat(String system, String user, Map<String, Object> schema) {
		Map<String, Object> request = Map.of("model", model, "stream", false, "keep_alive", KEEP_ALIVE, "format", schema,
				// Temperature 0 picks the likeliest answer every time, so the same transaction gets the same suggestion.
				"options", Map.of("temperature", 0),
				"messages", List.of(Map.of("role", "system", "content", system), Map.of("role", "user", "content", user)));
		String body;
		try {
			body = http.post()
				.uri("/api/chat")
				.contentType(MediaType.APPLICATION_JSON)
				.body(JSON.writeValueAsString(request))
				.retrieve()
				.body(String.class);
		}
		catch (ResourceAccessException ex) {
			throw new ModelUnavailableException(notRunning());
		}
		catch (RestClientResponseException ex) {
			String error = errorIn(ex.getResponseBodyAsString());
			if (ex.getStatusCode().value() == 404 && error.contains("not found")) {
				throw new ModelUnavailableException(notDownloaded());
			}
			throw new ModelUnavailableException("Ollama couldn't answer: " + error);
		}
		try {
			return JSON.readTree(body).path("message").path("content").asString("");
		}
		catch (JacksonException ex) {
			throw new ModelUnavailableException("Ollama's answer wasn't JSON. Try restarting it.");
		}
	}

	private String notRunning() {
		return "Ollama isn't running at " + baseUrl + ". Open the Ollama app, or run: ollama serve";
	}

	private String notDownloaded() {
		return "The model " + model + " isn't downloaded yet. Run: ollama pull " + model;
	}

	private static String errorIn(String body) {
		try {
			return JSON.readTree(body).path("error").asString(body);
		}
		catch (JacksonException ex) {
			return body;
		}
	}

	private static JdkClientHttpRequestFactory requestFactory(Duration timeout) {
		JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(
				HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build());
		factory.setReadTimeout(timeout);
		return factory;
	}

}
