package viettel.dac.prototype.llm.client;


import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

@Service
public class LLMClient {

    @Value("${llm.api.url}") private String apiUrl;
    @Value("${llm.api.key}") private String apiKey;

    private final WebClient webClient = WebClient.builder()
            .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
            .build();

    public String generate(String prompt) {
        return webClient.post()
                .uri(apiUrl)
                .bodyValue(createRequestBody(prompt))
                .retrieve()
                .bodyToMono(String.class)
                .block();
    }

    private String createRequestBody(String prompt) {
        return String.format("""
            {
                "model": "gpt-4",
                "messages": [{
                    "role": "user",
                    "content": "%s"
                }],
                "temperature": 0.7
            }
            """, prompt.replace("\"", "\\\""));
    }
}
