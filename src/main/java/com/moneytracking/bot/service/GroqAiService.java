package com.moneytracking.bot.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Service
public class GroqAiService {

    private static final Logger log = LoggerFactory.getLogger(GroqAiService.class);
    private final String apiKey;
    private final OkHttpClient client;
    private final ObjectMapper objectMapper;

    public GroqAiService(@Value("${groq.api.key}") String apiKey) {
        this.apiKey = apiKey;
        this.client = new OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .build();
        this.objectMapper = new ObjectMapper();
    }

    public AiResponse analyzeText(String userMessage) {
        if (apiKey == null || apiKey.isEmpty() || apiKey.startsWith("${")) {
            log.error("Groq API Key is missing!");
            return new AiResponse("chat", "Maaf, fitur AI sedang tidak tersedia karena API Key belum dikonfigurasi.", null, null, null);
        }

        String url = "https://api.groq.com/openai/v1/chat/completions";

        String systemPrompt = "Kamu adalah asisten pencatat keuangan (FinTrack Bot). Tugasmu adalah mengekstrak niat pengguna dari teks bahasa Indonesia yang diberikan.\n"
                + "JIKA teks tersebut adalah instruksi untuk mencatat pengeluaran atau pemasukan (contoh: 'makan 25rb', 'gaji masuk 5jt'), "
                + "kembalikan HANYA format JSON murni TANPA tanda kutip markdown (```json). Format: "
                + "{\"intent\": \"record\", \"type\": \"INCOME atau EXPENSE\", \"amount\": <angka nominal murni tanpa titik>, \"category\": \"<Tebak Kategori terdekat: Makanan, Transport, Belanja, Kebutuhan, Kesehatan, Hiburan, Gaji, Bonus, Lainnya>\", \"description\": \"<deskripsi singkat>\"}.\n"
                + "JIKA teks tersebut HANYA berupa pertanyaan keuangan, sapaan, atau basa-basi (contoh: 'halo', 'bagaimana cara hemat?'), "
                + "kembalikan HANYA format JSON murni: "
                + "{\"intent\": \"chat\", \"message\": \"<jawaban kamu sebagai asisten keuangan yang ramah>\"}.\n"
                + "Penting: Jawab HANYA dengan string JSON murni, jangan ada kalimat pembuka atau penutup.";

        try {
            // Build OpenAI-compatible JSON payload for Groq
            String jsonPayload = objectMapper.writeValueAsString(
                    new GroqRequest("llama-3.1-8b-instant", new Message[]{
                            new Message("system", systemPrompt),
                            new Message("user", userMessage)
                    }, 0.5)
            );

            RequestBody body = RequestBody.create(jsonPayload, MediaType.parse("application/json"));
            
            Request request = new Request.Builder()
                    .url(url)
                    .post(body)
                    .addHeader("Authorization", "Bearer " + apiKey)
                    .addHeader("Content-Type", "application/json")
                    .build();

            try (Response response = client.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    String errorBody = response.body() != null ? response.body().string() : "No error body";
                    log.error("Groq API call failed with status code {}: {}", response.code(), errorBody);
                    return new AiResponse("chat", "Maaf, AI sedang mengalami gangguan saat memproses permintaanmu. (" + response.code() + ")", null, null, null);
                }

                String responseBody = response.body().string();
                JsonNode rootNode = objectMapper.readTree(responseBody);
                
                // Extract text from OpenAI-compatible response structure
                String aiText = rootNode.path("choices").get(0).path("message").path("content").asText();
                
                // Clean up markdown code blocks if AI still includes them
                if (aiText.startsWith("```json")) {
                    aiText = aiText.substring(7);
                }
                if (aiText.startsWith("```")) {
                    aiText = aiText.substring(3);
                }
                if (aiText.endsWith("```")) {
                    aiText = aiText.substring(0, aiText.length() - 3);
                }
                aiText = aiText.trim();

                log.info("Groq Raw JSON Output: {}", aiText);

                // Parse the clean JSON into AiResponse object
                return objectMapper.readValue(aiText, AiResponse.class);
            }
        } catch (Exception e) {
            log.error("Error communicating with Groq: ", e);
            return new AiResponse("chat", "Maaf, sistem AI sedang sibuk atau terjadi kesalahan internal.", null, null, null);
        }
    }

    // --- Inner classes for request/response mapping ---

    public static class AiResponse {
        private String intent; 
        private String message; 
        private String type; 
        private Double amount;
        private String category;
        private String description;

        public AiResponse() {}

        public AiResponse(String intent, String message, String type, Double amount, String category) {
            this.intent = intent;
            this.message = message;
            this.type = type;
            this.amount = amount;
            this.category = category;
        }

        public String getIntent() { return intent; }
        public void setIntent(String intent) { this.intent = intent; }
        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
        public Double getAmount() { return amount; }
        public void setAmount(Double amount) { this.amount = amount; }
        public String getCategory() { return category; }
        public void setCategory(String category) { this.category = category; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
    }

    private record GroqRequest(String model, Message[] messages, double temperature) {}
    private record Message(String role, String content) {}
}
