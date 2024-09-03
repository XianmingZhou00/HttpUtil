# HttpUtil

## Overview
This is a library for encapsulating HTTP requests based on OkHttp3, designed to simplify the handling of network requests. It provides an easy-to-use interface supporting various request methods such as GET, POST, and more.

## Examples
```java
 // Set Log Level
 HttpUtil.setLogLevel("DEBUG");


 // GET request
 HttpUtil.get("https://www.google.com")
         .execute();

 HttpUtil.get("https://www.google.com")
         .param("name","okHttp") // add param
         .header(Map.of("User-Agent","Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36"))
         .execute();
 
 // POST request  x-www-form-urlencoded
 httpUtil.post(url)
         .form(Map.of("name","okHttp"))
         .execute();
 
 // POST request  json
 HttpUtil.post(url)
         .body(jsonString)
         .execute();

 // PUT request
 HttpUtil.put(url)
         .body(jsonString)
         .execute();
 
 // DELETE request
HttpUtil.delete(url)
        .execute();

 // Upload File 
 HttpUtil.uploadFile(url)
         .formData(Collections.singletonMap("description", "Test file"), Collections.singletonList(uploadFile))
         .execute();
 
 // support async http request
 CompletableFuture<Response> response = HttpUtil.get(url)
                                                .executeAsync();
```
## LICENSE
Apache License 2.0