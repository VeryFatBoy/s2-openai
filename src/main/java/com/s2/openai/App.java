package com.s2.openai;

import com.theokanning.openai.completion.chat.ChatCompletionChoice;
import com.theokanning.openai.completion.chat.ChatCompletionRequest;
import com.theokanning.openai.completion.chat.ChatMessage;
import com.theokanning.openai.completion.chat.ChatMessageRole;
import com.theokanning.openai.embedding.Embedding;
import com.theokanning.openai.embedding.EmbeddingRequest;
import com.theokanning.openai.service.OpenAiService;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class App {

    private static final String PORT = "3306";
    private static final String USER = "admin";
    private static final String DATABASE = "winter_wikipedia";

    private static final String EMBEDDING_MODEL = "text-embedding-ada-002";
    private static final String GPT_MODEL = "gpt-3.5-turbo";

    public static void main(String[] args) {
        String TOKEN = System.getenv("OPENAI_TOKEN");
        String HOSTNAME = System.getenv("S2_HOST");
        String PASSWORD = System.getenv("S2_PASSWORD");

        OpenAiService service = new OpenAiService(TOKEN);

        /*
         * 1. Ask ChatGPT a question and print the response
         */

        List<ChatMessage> messages = new ArrayList<>();
        ChatMessage systemMessage = new ChatMessage(ChatMessageRole.SYSTEM.value(),
                "Who won the gold medal for curling in Olympics 2022?");
        messages.add(systemMessage);

        ChatCompletionRequest chatCompletionRequest = ChatCompletionRequest
                .builder()
                .model(GPT_MODEL)
                .messages(messages)
                .build();

        List<ChatCompletionChoice> choices = service.createChatCompletion(chatCompletionRequest).getChoices();
        String response = choices.get(0).getMessage().getContent();
        System.out.println("ChatGPT says: " + response);

        try {
            String connectionUrl = String.format("jdbc:singlestore://%s:%s/%s", HOSTNAME, PORT, DATABASE);
            Connection conn = DriverManager.getConnection(connectionUrl, USER, PASSWORD);
            /*
             * 2. Get embedding for similar query string
             */

            EmbeddingRequest embeddingRequest = EmbeddingRequest
                    .builder()
                    .model(EMBEDDING_MODEL)
                    .input(Collections.singletonList("curling gold medal"))
                    .build();

            List<Embedding> embeddings = service.createEmbeddings(embeddingRequest).getData();
            String embedding = embeddings.get(0).getEmbedding().toString();

            /*
             * 3. Perform DOT_PRODUCT and print the results
             */

            String sqlQuery = "SELECT text, DOT_PRODUCT(JSON_ARRAY_PACK(?), embedding) AS score " +
                    "FROM winter_olympics_2022 " +
                    "ORDER BY score DESC LIMIT 5";

            PreparedStatement stmt = conn.prepareStatement(sqlQuery);

            stmt.setString(1, embedding);

            ResultSet resultSet = stmt.executeQuery();

            double score = 0.0;
            String text = "";
            String texts = "";

            while (resultSet.next()) {
                score = resultSet.getDouble("score");
                text = resultSet.getString("text");
                texts += text;

                System.out.println("Score: " + score);
                System.out.println("Text: " + text);
            }

            messages.clear();

            /*
             * 4. Give ChatGPT new info, repeat question and print response
             */

            systemMessage = new ChatMessage(ChatMessageRole.SYSTEM.value(),
                    "Use the below articles on the 2022 Winter Olympics to answer the subsequent question. If the answer cannot be found in the articles, write 'I could not find an answer.'");
            messages.add(systemMessage);

            systemMessage = new ChatMessage(ChatMessageRole.SYSTEM.value(), texts);
            messages.add(systemMessage);

            systemMessage = new ChatMessage(ChatMessageRole.SYSTEM.value(),
                    "Who won the gold medal for curling in Olympics 2022?");
            messages.add(systemMessage);

            chatCompletionRequest = ChatCompletionRequest
                    .builder()
                    .model(GPT_MODEL)
                    .messages(messages)
                    .build();

            choices = service.createChatCompletion(chatCompletionRequest).getChoices();
            response = choices.get(0).getMessage().getContent();
            System.out.println("ChatGPT says: " + response);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
