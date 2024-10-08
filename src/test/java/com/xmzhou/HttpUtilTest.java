package com.xmzhou;

import com.xmzhou.util.HttpUtil;
import okhttp3.HttpUrl;
import okhttp3.Response;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Description:
 * Author: Xianming Zhou
 * CreateTime: 2024/8/28 14:03
 */
class HttpUtilTest {
    private MockWebServer mockWebServer;

    @BeforeEach
    public void setUp() throws IOException {
        mockWebServer = new MockWebServer();
        mockWebServer.start();
    }

    @AfterEach
    public void tearDown() throws IOException {
        mockWebServer.shutdown();
    }

    @Test
    public void testEmptyUrlException() {
        assertThrows(IllegalArgumentException.class, () -> HttpUtil.get(""));
    }

    @Test
    public void testGetRequest() throws Exception {
        String expectedBody = "Hello World";
        mockWebServer.enqueue(new MockResponse().setBody(expectedBody));

        Response response = HttpUtil.get(buildUrl("/add"))
                .param("type", "test")
                .execute();

        assertEquals(200, response.code());
        assertEquals(expectedBody, response.body().string());
    }

    @Test
    public void testPostRequest() throws Exception {
        String expectedBody = "{\"message\":\"Success\"}";
        mockWebServer.enqueue(new MockResponse().setBody(expectedBody));

        String url = mockWebServer.url("/post").toString();
        Map<String, Object> formParams = new HashMap<>();
        formParams.put("key", "value");

        Response response = HttpUtil.post(url).form(formParams).execute();

        assertEquals(200, response.code());
        assertEquals(expectedBody, response.body().string());
    }

    @Test
    public void testPutRequest() throws Exception {
        String expectedBody = "{\"status\":\"updated\"}";
        mockWebServer.enqueue(new MockResponse().setBody(expectedBody));

        String url = mockWebServer.url("/put").toString();
        String body = "{\"name\":\"John\"}";

        Response response = HttpUtil.put(url).body(body).execute();

        assertEquals(200, response.code());
        assertEquals(expectedBody, response.body().string());
    }

    @Test
    public void testDeleteRequest() throws Exception {
        String expectedBody = "Resource deleted";
        mockWebServer.enqueue(new MockResponse().setBody(expectedBody));

        String url = mockWebServer.url("/delete").toString();
        Response response = HttpUtil.delete(url).execute();

        assertEquals(200, response.code());
        assertEquals(expectedBody, response.body().string());
    }

    @Test
    public void testUploadFile() throws Exception {
        mockWebServer.enqueue(new MockResponse()
                .setBody("File uploaded successfully")
                .setResponseCode(200));

        // 准备测试数据
        byte[] testData = "Hello, World!".getBytes(StandardCharsets.UTF_8);
        HttpUtil.UploadFile uploadFile = new HttpUtil.UploadFile();
        uploadFile.setName("file");
        uploadFile.setFileName("test.txt");
        uploadFile.setFileData(testData);

        // 构建请求
        Response response = HttpUtil.uploadFile(buildUrl("/upload"))
                .formData(Collections.singletonMap("description", "Test file"), Collections.singletonList(uploadFile))
                .execute();

        // 验证响应
        assertEquals(response.code(), 200);
        assertEquals(response.body().string(), "File uploaded successfully");

        // 验证请求
        RecordedRequest recordedRequest = mockWebServer.takeRequest();
        assertEquals(recordedRequest.getMethod(), "POST");
        assertEquals(recordedRequest.getPath(), "/upload");

        Assertions.assertAll("Request Body",
                () -> assertTrue(recordedRequest.getBody().readUtf8().contains("filename=\"test.txt\""))
        );
    }

    @Test
    public void testUploadFileWithEmptyFileName() throws Exception {
        HttpUtil.UploadFile uploadFile = new HttpUtil.UploadFile();
        uploadFile.setName("file");
        uploadFile.setFileName("");
        uploadFile.setFileData(new byte[]{});

        mockWebServer.enqueue(new MockResponse().setResponseCode(400));

        String url = buildUrl("/upload_empty");
        Response response = HttpUtil.uploadFile(url)
                .formData(Collections.singletonMap("description", "Test file"), Collections.singletonList(uploadFile))
                .execute();

        assertEquals(400, response.code());
    }

    @Test
    public void testExecuteAsyncSuccess() throws Exception {
        String url = mockWebServer.url("/").toString();
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setBody("response body"));

        CompletableFuture<Response> futureResponse = HttpUtil.get(url)
                .executeAsync();

        Response response = futureResponse.get();
        assertNotNull(response);
        assertTrue(response.isSuccessful());
        assertEquals("response body", response.body().string());
    }

    @Test
    public void testExecuteAsyncNetworkError() {
        String url = "http://localhost:9999"; // Invalid URL to simulate network error

        CompletableFuture<Response> futureResponse = HttpUtil.get(url)
                .executeAsync();

        assertThrows(Exception.class, futureResponse::get);
    }

    @Test
    public void testReadTimeoutSucceed() throws Exception {
        mockWebServer.enqueue(new MockResponse().setBody("response body").setBodyDelay(3, TimeUnit.SECONDS));
        HttpUrl url = mockWebServer.url("/timeout");
        Response response = HttpUtil.get(url.toString())
                .readTimeoutSeconds(4)
                .connectTimeoutSeconds(10)
                .execute();
        assertEquals("response body", response.body().string());
    }

    @Test
    public void testReadTimeoutFailure() {
        mockWebServer.enqueue(new MockResponse().setBody("response body").setBodyDelay(3, TimeUnit.SECONDS));
        HttpUrl url = mockWebServer.url("/timeout");
        try {
            HttpUtil.get(url.toString())
                    .readTimeoutSeconds(2)
                    .execute();
            fail("Expected SocketTimeoutException to be thrown");
        } catch (Exception e) {
            if (e instanceof SocketTimeoutException) {
                // ok
            } else {
                fail("Unexpected exception thrown: " + e.getMessage());
            }
        }
    }

    @Test
    public void testLogLevel() throws Exception {
        HttpUtil.setLogLevel("debug");
        HttpUtil.get("https://www.baidu.com").execute();
    }

    private String buildUrl(String path) {
        String host = mockWebServer.getHostName();
        int port = mockWebServer.getPort();
        return "http://" + host + ":" + port + path;
    }
}

