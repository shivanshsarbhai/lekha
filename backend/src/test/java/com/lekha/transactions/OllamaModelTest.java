package com.lekha.transactions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.io.IOException;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class OllamaModelTest {

	private MockRestServiceServer ollama;

	private OllamaModel model;

	@BeforeEach
	void setUp() {
		RestClient.Builder http = RestClient.builder();
		ollama = MockRestServiceServer.bindTo(http).build();
		model = new OllamaModel(http, "http://ollama.test", "qwen2.5:7b");
	}

	@Test
	void sendsTheConversationWithTheSchemaAndReturnsTheReply() {
		ollama.expect(requestTo("http://ollama.test/api/chat"))
			.andExpect(method(HttpMethod.POST))
			.andExpect(jsonPath("$.model").value("qwen2.5:7b"))
			.andExpect(jsonPath("$.stream").value(false))
			.andExpect(jsonPath("$.options.temperature").value(0))
			.andExpect(jsonPath("$.format.type").value("object"))
			.andExpect(jsonPath("$.messages[0].role").value("system"))
			.andExpect(jsonPath("$.messages[0].content").value("You classify"))
			.andExpect(jsonPath("$.messages[1].role").value("user"))
			.andExpect(jsonPath("$.messages[1].content").value("Transaction 1"))
			.andRespond(withSuccess("""
					{"model": "qwen2.5:7b", "message": {"role": "assistant", "content": "{\\"answers\\": []}"}, "done": true}
					""", MediaType.APPLICATION_JSON));

		String reply = model.chat("You classify", "Transaction 1", Map.of("type", "object"));

		assertThat(reply).isEqualTo("{\"answers\": []}");
		ollama.verify();
	}

	@Test
	void saysHowToDownloadAModelThatIsMissing() {
		ollama.expect(requestTo("http://ollama.test/api/chat"))
			.andRespond(withStatus(HttpStatus.NOT_FOUND).contentType(MediaType.APPLICATION_JSON)
				.body("{\"error\": \"model \\\"qwen2.5:7b\\\" not found, try pulling it first\"}"));

		assertThatThrownBy(() -> model.chat("system", "user", Map.of()))
			.isInstanceOf(ModelUnavailableException.class)
			.hasMessageContaining("ollama pull qwen2.5:7b");
	}

	@Test
	void saysHowToStartOllamaWhenItIsntRunning() {
		ollama.expect(requestTo("http://ollama.test/api/chat")).andRespond(withException(new IOException("Connection refused")));

		assertThatThrownBy(() -> model.chat("system", "user", Map.of()))
			.isInstanceOf(ModelUnavailableException.class)
			.hasMessageContaining("Ollama isn't running at http://ollama.test");
	}

	@Test
	void passesOnOtherErrors() {
		ollama.expect(requestTo("http://ollama.test/api/chat"))
			.andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR).contentType(MediaType.APPLICATION_JSON)
				.body("{\"error\": \"out of memory\"}"));

		assertThatThrownBy(() -> model.chat("system", "user", Map.of()))
			.isInstanceOf(ModelUnavailableException.class)
			.hasMessage("Ollama couldn't answer: out of memory");
	}

	@Test
	void isAvailableWhenTheModelIsDownloaded() {
		ollama.expect(requestTo("http://ollama.test/api/tags"))
			.andRespond(withSuccess("{\"models\": [{\"name\": \"llama3.2:latest\"}, {\"name\": \"qwen2.5:7b\"}]}",
					MediaType.APPLICATION_JSON));

		assertThat(model.status()).isEqualTo(new LanguageModel.Status(true, null));
	}

	@Test
	void findsAModelNamedWithoutATagUnderLatest() {
		RestClient.Builder http = RestClient.builder();
		MockRestServiceServer server = MockRestServiceServer.bindTo(http).build();
		server.expect(requestTo("http://ollama.test/api/tags"))
			.andRespond(withSuccess("{\"models\": [{\"name\": \"llama3.2:latest\"}]}", MediaType.APPLICATION_JSON));

		assertThat(new OllamaModel(http, "http://ollama.test", "llama3.2").status().available()).isTrue();
	}

	@Test
	void isUnavailableUntilTheModelIsDownloaded() {
		ollama.expect(requestTo("http://ollama.test/api/tags"))
			.andRespond(withSuccess("{\"models\": [{\"name\": \"llama3.2:latest\"}]}", MediaType.APPLICATION_JSON));

		LanguageModel.Status status = model.status();

		assertThat(status.available()).isFalse();
		assertThat(status.problem()).contains("ollama pull qwen2.5:7b");
	}

	@Test
	void isUnavailableWhenOllamaIsntRunning() {
		ollama.expect(requestTo("http://ollama.test/api/tags")).andRespond(withException(new IOException("Connection refused")));

		LanguageModel.Status status = model.status();

		assertThat(status.available()).isFalse();
		assertThat(status.problem()).contains("ollama serve");
	}

}
