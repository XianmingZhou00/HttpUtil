package com.xmzhou.util;

import ch.qos.logback.classic.Level;
import com.moczul.ok2curl.CurlInterceptor;
import okhttp3.*;
import okhttp3.logging.HttpLoggingInterceptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * <h3> HTTP Request Tool, based on okhttp3.</h3>
 *
 * <p>
 * Utility class for making HTTP requests using OkHttp.
 * This class provides static methods to create request builders for different HTTP methods.
 * </p>
 *
 * <pre>
 *  Usage:
 * <code>HttpUtil.get(url)
 *          .execute();
 *  </br>
 *  HttpUtil.post(url)
 *          .form(Map)
 *          .execute()</code>
 * </pre>
 * Author: Xianming Zhou
 * CreateTime: 2024/8/28 10:40
 */

public class HttpUtil {
    private static final Logger LOG = LoggerFactory.getLogger(HttpUtil.class);

    private HttpUtil() {
    }

    /**
     * Creates a GET request builder.
     *
     * @param url the URL to send the GET request to
     * @return a RequestBuilder for the GET request
     */
    public static RequestBuilder get(String url) {
        return new RequestBuilder(url, HttpMethod.GET);
    }

    /**
     * Creates a POST request builder.
     *
     * @param url the URL to send the POST request to
     * @return a RequestBuilder for the POST request
     */
    public static RequestBuilder post(String url) {
        return new RequestBuilder(url, HttpMethod.POST);
    }

    /**
     * Creates a PUT request builder.
     *
     * @param url the URL to send the PUT request to
     * @return a RequestBuilder for the PUT request
     */
    public static RequestBuilder put(String url) {
        return new RequestBuilder(url, HttpMethod.PUT);
    }

    /**
     * Creates a DELETE request builder.
     *
     * @param url the URL to send the DELETE request to
     * @return a RequestBuilder for the DELETE request
     */
    public static RequestBuilder delete(String url) {
        return new RequestBuilder(url, HttpMethod.DELETE);
    }

    /**
     * Creates a POST request builder.
     *
     * @param url the URL to send the POST request to
     * @return a RequestBuilder for the POST request
     */
    public static RequestBuilder uploadFile(String url) {
        return new RequestBuilder(url, HttpMethod.POST);
    }

    public static void setLogLevel(String level) {
        ch.qos.logback.classic.Logger logger = (ch.qos.logback.classic.Logger) LOG;
        switch (level.toUpperCase()) {
            case "TRACE":
                logger.setLevel(Level.TRACE);
                break;
            case "DEBUG":
                logger.setLevel(Level.DEBUG);
                break;
            case "INFO":
                logger.setLevel(Level.INFO);
                break;
            case "WARN":
                logger.setLevel(Level.WARN);
                break;
            case "ERROR":
                logger.setLevel(Level.ERROR);
                break;
            case "OFF":
                logger.setLevel(Level.OFF);
                break;
            default:
                throw new IllegalArgumentException("Unsupported log level: " + level);
        }
    }

    /**
     * Builder class for constructing HTTP requests.
     */
    public static class RequestBuilder {
        private static volatile OkHttpClient httpClient;
        private String url;
        private final Request.Builder requestBuilder;
        private RequestBody requestBody;
        private final HttpMethod httpMethod;
        private HttpConfig httpConfig;

        private RequestBuilder(String url, HttpMethod httpMethod) {
            if (Objects.isNull(url) || url.isEmpty()) {
                throw new IllegalArgumentException("request url is null");
            }
            this.url = url;
            initHttpClient();
            this.httpMethod = httpMethod;
            requestBuilder = new Request.Builder();
            requestBuilder.url(this.url);
            httpConfig = HttpConfig.builder().build();
            requestBuilder.tag(HttpConfig.class, httpConfig);
        }

        private Interceptor httpLoggingInterceptor() {
            HttpLoggingInterceptor httpLoggingInterceptor = new HttpLoggingInterceptor(s -> {
                if (LOG.isDebugEnabled()) {
                    LOG.debug(s);
                }
            });
            httpLoggingInterceptor.setLevel(HttpLoggingInterceptor.Level.BODY);
            return httpLoggingInterceptor;
        }

        private Interceptor httpTimeoutConfigInterceptor() {
            return chain -> {
                Request request = chain.request();
                HttpConfig config = request.tag(HttpConfig.class);
                config = config == null ? new HttpConfig() : config;
                return chain
                        .withConnectTimeout(Optional.of(config.getConnectTimeoutSeconds()).orElse(60), TimeUnit.SECONDS)
                        .withReadTimeout(Optional.of(config.getReadTimeoutSeconds()).orElse(60), TimeUnit.SECONDS)
                        .withWriteTimeout(Optional.of(config.getWriteTimeoutSeconds()).orElse(60), TimeUnit.SECONDS)
                        .proceed(request);
            };
        }

        /**
         * Initializes the OkHttpClient instance in a thread-safe manner.
         */
        private void initHttpClient() {
            if (httpClient == null) {
                synchronized (HttpUtil.class) {
                    if (httpClient == null) {
                        if (LOG.isDebugEnabled()) {
                            LOG.debug("Init OkHttpClient");
                        }
                        httpClient = new OkHttpClient.Builder()
                                .addInterceptor(httpLoggingInterceptor())
                                .addInterceptor(httpTimeoutConfigInterceptor())
                                .addNetworkInterceptor(new CurlInterceptor(s -> {
                                    if (LOG.isDebugEnabled()) {
                                        LOG.debug(s);
                                    }
                                }))
                                .build();
                        if (LOG.isDebugEnabled()) {
                            LOG.debug("Init OkHttpClient Successfully");
                        }
                    }
                }
            }
        }

        /**
         * Adds headers to the request.
         *
         * @param header a map of headers to add
         * @return the current RequestBuilder instance
         */
        public RequestBuilder header(Map<String, String> header) {
            if (Objects.nonNull(header)) {
                Set<Map.Entry<String, String>> entrySet = header.entrySet();
                for (Map.Entry<String, String> entry : entrySet) {
                    requestBuilder.addHeader(entry.getKey(), String.valueOf(entry.getValue()));
                }
            }
            return this;
        }

        /**
         * Add a query parameter to the request URL.
         *
         * @param key   the query parameter key
         * @param value the query parameter value
         * @return the current RequestBuilder instance
         */
        public RequestBuilder param(String key, String value) {
            HttpUrl.Builder urlBuilder = HttpUrl.parse(this.url).newBuilder();
            urlBuilder.addQueryParameter(key, value);
            HttpUrl httpUrl = urlBuilder.build();
            requestBuilder.url(httpUrl);
            this.url = httpUrl.toString();
            return this;
        }


        /**
         * Adds multiple query parameters to the request URL.
         *
         * @param params a map of query parameters to add
         * @return the current RequestBuilder instance
         */
        public RequestBuilder params(Map<String, ?> params) {
            HttpUrl.Builder urlBuilder = HttpUrl.parse(this.url).newBuilder();
            if (Objects.nonNull(params) && !params.isEmpty()) {
                for (Map.Entry<String, ?> entry : params.entrySet()) {
                    urlBuilder.addQueryParameter(entry.getKey(), String.valueOf(entry.getValue()));
                }
            }
            HttpUrl httpUrl = urlBuilder.build();
            requestBuilder.url(httpUrl);
            this.url = httpUrl.toString();
            return this;
        }

        /**
         * Sets the request body as form parameters.
         *
         * @param form a map of form parameters
         * @return the current RequestBuilder instance
         */
        public RequestBuilder form(Map<String, ?> form) {
            FormBody.Builder paramBuilder = new FormBody.Builder(StandardCharsets.UTF_8);
            for (Map.Entry<String, ?> entry : form.entrySet()) {
                paramBuilder.add(entry.getKey(), String.valueOf(entry.getValue()));
            }
            requestBody = paramBuilder.build();
            return this;
        }

        /**
         * Sets the request body as multipart form data, including files.
         *
         * @param form  a map of form parameters
         * @param files a list of files to upload
         * @return the current RequestBuilder instance
         */
        public RequestBuilder formData(Map<String, ?> form, List<UploadFile> files) {
            MultipartBody.Builder bodyBuilder = new MultipartBody.Builder();
            bodyBuilder.setType(MultipartBody.FORM);
            for (UploadFile uploadFile : files) {
                bodyBuilder.addFormDataPart(uploadFile.getName(),
                        uploadFile.getFileName(),
                        RequestBody.create(null, uploadFile.getFileData())
                );
            }
            for (Map.Entry<String, ?> entry : form.entrySet()) {
                bodyBuilder.addFormDataPart(entry.getKey(), String.valueOf(entry.getValue()));
            }
            requestBody = bodyBuilder.build();
            return this;
        }

        /**
         * Sets the request body as a JSON string.
         *
         * @param body the JSON string to set as the request body
         * @return the current RequestBuilder instance
         */
        public RequestBuilder body(String body) {
            MediaType mediaType = MediaType.parse("application/json; charset=utf-8");
            requestBody = RequestBody.create(mediaType, body);
            return this;
        }

        /**
         * Sets the connection timeout for the request.
         *
         * @param connectTimeoutSeconds the connection timeout in seconds
         * @return the current RequestBuilder instance
         */
        public RequestBuilder connectTimeoutSeconds(int connectTimeoutSeconds) {
            httpConfig.setConnectTimeoutSeconds(connectTimeoutSeconds);
            return this;
        }

        /**
         * Sets the read timeout for the request.
         *
         * @param readTimeoutSeconds the read timeout in seconds
         * @return the current RequestBuilder instance
         */
        public RequestBuilder readTimeoutSeconds(int readTimeoutSeconds) {
            httpConfig.setReadTimeoutSeconds(readTimeoutSeconds);
            return this;
        }

        /**
         * Sets the write timeout for the request.
         *
         * @param writeTimeoutSeconds the write timeout in seconds
         * @return the current RequestBuilder instance
         */
        public RequestBuilder writeTimeoutSeconds(int writeTimeoutSeconds) {
            httpConfig.setWriteTimeoutSeconds(writeTimeoutSeconds);
            return this;
        }

        private Request buildRequest() {
            Request request;
            switch (httpMethod) {
                case GET:
                    request = requestBuilder.get().build();
                    break;
                case POST:
                    request = requestBuilder.post(requestBody).build();
                    break;
                case PUT:
                    request = requestBuilder.put(requestBody).build();
                    break;
                case DELETE:
                    request = requestBuilder.delete(requestBody).build();
                    break;
                default:
                    throw new IllegalArgumentException("http method not support");
            }
            return request;
        }

        /**
         * Executes the HTTP request and returns the response.
         *
         * @return the HTTP response
         * @throws Exception if an error occurs during the request
         */
        public Response execute() throws Exception {
            Request request = buildRequest();
            try (Response response = httpClient.newCall(request).execute()) {
                if (response.body() == null) {
                    LOG.error("Response body is null");
                    throw new IllegalStateException("Response body is null");
                }
                ResponseBody newBody = ResponseBody.create(response.body().contentType(), response.body().bytes());
                return response.newBuilder()
                        .body(newBody)
                        .build();
            } catch (Exception e) {
                LOG.error("HTTP Request Execute Failed", e);
                throw e;
            }
        }

        /**
         * Async Executes the HTTP request and returns the response.
         *
         * @return CompletableFuture
         * @throws Exception if an error occurs during the request
         */
        public CompletableFuture<Response> executeAsync() {
            CompletableFuture<Response> future = new CompletableFuture<>();
            Request request = buildRequest();
            httpClient.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    future.completeExceptionally(e);
                }

                @Override
                public void onResponse(Call call, Response response) {
                    try {
                        ResponseBody responseBody = response.body();
                        if (responseBody == null) {
                            LOG.error("Response body is null");
                            throw new IllegalStateException("Response body is null");
                        }
                        ResponseBody newBody = ResponseBody.create(responseBody.contentType(), responseBody.bytes());
                        Response copiedResponse = response.newBuilder()
                                .body(newBody)
                                .build();
                        future.complete(copiedResponse);
                    } catch (Exception e) {
                        future.completeExceptionally(new IOException("Response body is null"));
                    } finally {
                        // 确保关闭响应体
                        response.close();
                    }
                }
            });
            return future;
        }
    }

    /**
     * Configuration class for HTTP settings.
     */
    public static class HttpConfig {
        private int connectTimeoutSeconds = 60;
        private int readTimeoutSeconds = 60;
        private int writeTimeoutSeconds = 60;

        public int getConnectTimeoutSeconds() {
            return connectTimeoutSeconds;
        }

        public void setConnectTimeoutSeconds(int connectTimeoutSeconds) {
            this.connectTimeoutSeconds = connectTimeoutSeconds;
        }

        public int getReadTimeoutSeconds() {
            return readTimeoutSeconds;
        }

        public void setReadTimeoutSeconds(int readTimeoutSeconds) {
            this.readTimeoutSeconds = readTimeoutSeconds;
        }

        public int getWriteTimeoutSeconds() {
            return writeTimeoutSeconds;
        }

        public void setWriteTimeoutSeconds(int writeTimeoutSeconds) {
            this.writeTimeoutSeconds = writeTimeoutSeconds;
        }

        public static Builder builder() {
            return new Builder();
        }


        public static class Builder {
            private final HttpConfig httpConfig;

            public Builder() {
                httpConfig = new HttpConfig();
            }

            public Builder connectTimeoutSeconds(int connectTimeoutSeconds) {
                httpConfig.setConnectTimeoutSeconds(connectTimeoutSeconds);
                return this;
            }

            public Builder readTimeoutSeconds(int readTimeoutSeconds) {
                httpConfig.setReadTimeoutSeconds(readTimeoutSeconds);
                return this;
            }

            public Builder writeTimeoutSeconds(int writeTimeoutSeconds) {
                httpConfig.setWriteTimeoutSeconds(writeTimeoutSeconds);
                return this;
            }

            public HttpConfig build() {
                return httpConfig;
            }
        }
    }

    /**
     * Represents a file to be uploaded.
     */
    public static class UploadFile implements Serializable {
        private static final long serialVersionUID = 1L;

        private String name;

        private String fileName;

        private byte[] fileData;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getFileName() {
            return fileName;
        }

        public void setFileName(String fileName) {
            this.fileName = fileName;
        }

        public byte[] getFileData() {
            return fileData;
        }

        public void setFileData(byte[] fileData) {
            this.fileData = fileData;
        }
    }

    /**
     * Enum representing the HTTP methods supported by this utility.
     */
    private enum HttpMethod {
        GET, POST, PUT, DELETE
    }
}
