package com.moneytracking.bot.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;
import java.util.Map;
import java.util.List;

@Service
public class GeminiAiService {

    private static final Logger log = LoggerFactory.getLogger(GeminiAiService.class);
    private final String apiKey;
    private final OkHttpClient client;
    private final ObjectMapper objectMapper;

    public GeminiAiService(@Value("${gemini.api.key}") String apiKey) {
        this.apiKey = apiKey;
        this.client = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .build();
        this.objectMapper = new ObjectMapper();
    }

    public AiResponse analyzeText(String userMessage, String currentBalance) {
        if (apiKey == null || apiKey.isEmpty() || apiKey.startsWith("${")) {
            log.error("Gemini API Key is missing!");
            return new AiResponse("chat", "Maaf, fitur AI sedang tidak tersedia karena API Key belum dikonfigurasi.", null, null, null);
        }

        String url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.6-flash:generateContent?key=" + apiKey;

        String systemPrompt = "Kamu adalah AI assistant untuk Money Tracking Telegram Bot.\n\n"
                + "Tugasmu memahami kalimat natural user terkait transaksi uang atau obrolan keuangan.\n"
                + "User mungkin memasukkan chat kompleks seperti: 'Hari ini keluar duit 500rb buat makan', 'Tadi gue habis 50 ribu buat kopi', 'Barusan bayar listrik 350k', atau 'Gue dapat gaji 5 juta tadi'.\n\n"
                + "Jika user mencatat transaksi:\n"
                + "- Pahami konteks dan variasi bahasanya.\n"
                + "- JANGAN pernah mengarang nominal. Ekstrak nominal uang persis seperti yang diucapkan.\n"
                + "- Jika kalimatnya membingungkan dan nominal tidak jelas, tanyakan klarifikasi dengan bahasa santai, JANGAN tebak-tebak.\n\n"
                + "Jika user bertanya tentang keuangan:\n"
                + "- Gunakan data yang tersedia, jangan mengarang angka.\n"
                + "- Pahami pertanyaan berdasarkan konteks percakapan sebelumnya.\n"
                + "- Berikan analisis, saran, atau solusi jika diperlukan.\n"
                + "- Jika user sedang mengalami masalah keuangan, bantu mencari solusi secara bertahap.\n\n"
                + "GAYA RESPON (Untuk Chat):\n"
                + "- Berbicara seperti teman, bukan customer service.\n"
                + "- Santai, humanis, jenaka, dan humoris.\n"
                + "- Biasanya cukup 1-2 kalimat. Gunakan emoji yang sesuai.\n"
                + "- Buat respons secara dinamis, jangan menggunakan template yang sama.\n"
                + "- Humor harus sesuai dengan kondisi user dan jangan merendahkan.\n"
                + "- Jika kondisi keuangan user buruk/minus, tetap jujur tetapi berikan respons yang bisa membuat user tersenyum sekaligus membantu.\n"
                + "- Jangan selalu memberikan nasihat atau pertanyaan di akhir.\n\n"
                + "Data dari Database saat ini:\n"
                + "Saldo user: " + currentBalance + "\n\n"
                + "ATURAN OUTPUT: WAJIB KEMBALIKAN JSON MURNI DENGAN FORMAT BERIKUT (TIDAK ADA TEKS LAIN):\n\n"
                + "1. JIKA user MENCATAT transaksi:\n"
                + "   {\"intent\": \"record\", \"type\": \"INCOME atau EXPENSE\", \"amount\": <nominal murni tanpa format, misal 500000>, \"category\": \"<Tebak kategorinya>\", \"description\": \"<deskripsi singkat>\"}\n\n"
                + "2. JIKA user BERTANYA tentang keuangan/saldonya ATAU AI bingung nominal transaksi:\n"
                + "   {\"intent\": \"chat\", \"message\": \"(ISI DENGAN JAWABANMU ATAU PERTANYAAN KLARIFIKASI DISINI)\"}\n\n"
                + "3. JIKA di luar konteks Money Tracking:\n"
                + "   - Jangan menjawab pertanyaan tersebut.\n"
                + "   - Tetap gunakan gaya santai dan humoris.\n"
                + "   - Arahkan kembali ke Money Tracking. Jika user ingin bertanya hal lain, arahkan ke owner: @areUlookingFor.\n"
                + "   {\"intent\": \"out_of_context\", \"message\": \"(ISI DENGAN BALASAN JENAKA PENOLAKAN DISINI)\"}";

        try {
            // Build Gemini JSON payload
            Map<String, Object> payload = Map.of(
                "system_instruction", Map.of(
                    "parts", Map.of("text", systemPrompt)
                ),
                "contents", List.of(
                    Map.of(
                        "parts", List.of(Map.of("text", userMessage))
                    )
                ),
                "generationConfig", Map.of(
                    "responseMimeType", "application/json",
                    "temperature", 0.7
                )
            );

            String jsonPayload = objectMapper.writeValueAsString(payload);

            RequestBody body = RequestBody.create(jsonPayload, MediaType.parse("application/json"));
            
            Request request = new Request.Builder()
                    .url(url)
                    .post(body)
                    .addHeader("Content-Type", "application/json")
                    .build();

            // Auto-Retry Logic (Max 3 attempts for 5xx errors)
            int maxRetries = 3;
            int attempt = 0;
            Response response = null;
            boolean success = false;
            
            while (attempt < maxRetries && !success) {
                attempt++;
                try {
                    // Initialize a fresh call for each attempt
                    Call call = client.newCall(request);
                    response = call.execute();
                    
                    if (response.isSuccessful()) {
                        success = true;
                        // Process the successful response
                        String responseBody = response.body().string();
                        JsonNode rootNode = objectMapper.readTree(responseBody);
                        
                        // Extract text from Gemini structure
                        String aiText = rootNode.path("candidates").get(0)
                                .path("content").path("parts").get(0)
                                .path("text").asText();
                        
                        aiText = aiText.trim();
                        
                        // Safety cleanup
                        if (aiText.startsWith("```json")) {
                            aiText = aiText.substring(7).trim();
                        } else if (aiText.startsWith("```")) {
                            aiText = aiText.substring(3).trim();
                        }
                        if (aiText.endsWith("```")) {
                            aiText = aiText.substring(0, aiText.length() - 3).trim();
                        }
        
                        int startIndex = aiText.indexOf('{');
                        int endIndex = aiText.lastIndexOf('}');
                        if (startIndex != -1 && endIndex != -1 && startIndex < endIndex) {
                            aiText = aiText.substring(startIndex, endIndex + 1);
                        }
        
                        log.info("Gemini Raw JSON Output: {}", aiText);
        
                        // Close response explicitly since we aren't using try-with-resources here
                        response.close();
                        
                        return objectMapper.readValue(aiText, AiResponse.class);
                        
                    } else {
                        // Check if it's a server error (5xx) or Rate Limit (429) that is worth retrying
                        int code = response.code();
                        String errorBody = response.body() != null ? response.body().string() : "No error body";
                        response.close(); // Close immediately to avoid leak
                        
                        if ((code >= 500 && code <= 599) || code == 429) {
                            log.warn("Gemini API call failed with status code {}: {}. Attempt {} of {}", code, errorBody, attempt, maxRetries);
                            if (attempt < maxRetries) {
                                // Wait before retrying (exponential backoff: 1s, 2s)
                                Thread.sleep(attempt * 1000L);
                            } else {
                                log.error("Max retries reached. Gemini API call failed with status code {}: {}", code, errorBody);
                                return new AiResponse("chat", "Maaf, AI sedang mengalami gangguan saat memproses permintaanmu. (" + code + ")", null, null, null);
                            }
                        } else {
                            // Client errors (4xx) usually shouldn't be retried
                            log.error("Gemini API call failed with client error {}: {}", code, errorBody);
                            return new AiResponse("chat", "Maaf, ada kesalahan konfigurasi atau format permintaan ke AI. (" + code + ")", null, null, null);
                        }
                    }
                } catch (Exception e) {
                    if (response != null) {
                        response.close();
                    }
                    if (attempt >= maxRetries) {
                        log.error("Error communicating with Gemini after {} attempts: ", attempt, e);
                        return new AiResponse("chat", "Maaf, sistem AI sedang sibuk atau terjadi kesalahan internal.", null, null, null);
                    }
                    log.warn("Exception during Gemini API call on attempt {}: {}", attempt, e.getMessage());
                    try {
                        Thread.sleep(attempt * 1000L);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                    }
                }
            }
            return new AiResponse("chat", "Gagal memproses permintaan setelah beberapa percobaan.", null, null, null);
        } catch (Exception e) {
            log.error("Error preparing Gemini request: ", e);
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
}
