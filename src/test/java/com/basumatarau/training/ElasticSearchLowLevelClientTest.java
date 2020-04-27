package com.basumatarau.training;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.http.entity.ContentType;
import org.apache.http.nio.entity.NStringEntity;
import org.apache.http.util.EntityUtils;
import org.elasticsearch.client.Request;
import org.elasticsearch.client.Response;
import org.elasticsearch.client.ResponseListener;
import org.elasticsearch.client.RestClient;
import org.junit.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;

import static org.hamcrest.CoreMatchers.*;
import static org.hamcrest.MatcherAssert.assertThat;

public class ElasticSearchLowLevelClientTest {

    private RestClient elasticSearchRestClient = ElasticsearchClientProvider.INSTANCE.getRestClientBlocking();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    public void whenDomainObjectPersisted_thenDomainObjectGetsRetrieved() throws IOException {
        Request request = new Request("POST", "/test/_doc/");
        request.addParameter("pretty", "true");
        request.setEntity(new NStringEntity( "{\"json\":\"text\"}", ContentType.APPLICATION_JSON));

        Response response = elasticSearchRestClient.performRequest(request);
        String stringEntity = EntityUtils.toString(response.getEntity());
        assertThat(stringEntity, notNullValue());
        System.out.println(stringEntity);
    }

    @Test
    public void whenDomainObjectPersisted_thenDomainObjectGetsRetrievedAsync() throws IOException {

        int reqCount = 5;
        CountDownLatch latch = new CountDownLatch(reqCount);
        List<String> ids = new ArrayList<>();
        final StringBuilder builder = new StringBuilder();

        for (int i = 0; i < reqCount; i++) {
            elasticSearchRestClient.performRequestAsync(getRequest(i), new ResponseListener() {
                @Override
                public void onSuccess(Response response) {
                    String id;
                    id = getIdAsString(response, builder, objectMapper);
                    ids.add(id);
                    latch.countDown();
                }

                @Override
                public void onFailure(Exception e) {
                    latch.countDown();
                }
            });
        }

        try {
            latch.await();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }

        Request mGet = new Request("GET", "/test/_mget");
        Map<String, List<String>> mGetQueryBody = new HashMap<>();
        mGetQueryBody.put("ids", ids);
        mGet.addParameter("pretty", "true");
        mGet.setEntity(new NStringEntity(objectMapper.writeValueAsString(mGetQueryBody),
                                          ContentType.APPLICATION_JSON));
        final Response response = elasticSearchRestClient.performRequest(mGet);
        String content;
        content = getContentAsString(builder, response);
        assertThat(content, notNullValue());
        System.out.println(content);
    }

    private String getContentAsString(StringBuilder builder, Response response) {
        String content;
        try(BufferedReader bufferedReader =
                    new BufferedReader(new InputStreamReader(response.getEntity().getContent()))){
            builder.setLength(0);
            String line;
            while((line = bufferedReader.readLine()) != null){
                builder.append(line);
            }
            content = builder.toString();
        }catch (IOException e){
            throw new RuntimeException(e);
        }
        return content;
    }

    private String getIdAsString(Response response, StringBuilder builder, ObjectMapper objectMapper) {
        String id;
        try(BufferedReader bufferedReader =
                    new BufferedReader(new InputStreamReader(response.getEntity().getContent()))){
            builder.setLength(0);
            String line;
            while((line = bufferedReader.readLine()) != null){
                builder.append(line);
            }
            id = ((String) objectMapper
                    .readValue(builder.toString(), new TypeReference<Map<String, Object>>() {
                    }).get("_id"));
        }catch (IOException e){
            throw new RuntimeException(e);
        }
        return id;
    }

    private Request getRequest(int count) {
        Request request = new Request("POST", "/test/_doc/");
        request.addParameter("pretty", "true");
        request.setEntity(new NStringEntity(String.format("{\"customCountField\":\"%d\"}", count),
                                            ContentType.APPLICATION_JSON));
        return request;
    }
}
