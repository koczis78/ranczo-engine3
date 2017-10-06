package pl.redeem.ranczo.pushbullet;

import com.github.sheigutn.pushbullet.Pushbullet;
import com.github.sheigutn.pushbullet.exception.PushbulletApiError;
import com.github.sheigutn.pushbullet.exception.PushbulletApiException;
import com.github.sheigutn.pushbullet.http.EntityEnclosingRequest;
import com.github.sheigutn.pushbullet.http.Request;
import com.github.sheigutn.pushbullet.http.defaults.post.RequestFileUploadRequest;
import com.github.sheigutn.pushbullet.items.file.AwsAuthData;
import com.github.sheigutn.pushbullet.items.file.UploadFile;
import com.github.sheigutn.pushbullet.util.ClassUtil;
import org.apache.http.HttpHeaders;
import org.apache.http.HttpHost;
import org.apache.http.HttpStatus;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.*;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.net.URI;

/**
 * Created by mkszczot on 2017-10-06.
 */
public class PushbulletProxy extends Pushbullet{

    private static final String API_BASE_URL = "https://api.pushbullet.com/v2";
    private static final String ACCESS_TOKEN_HEADER = "Access-Token";

    private final String accessToken;

    public PushbulletProxy(String accessToken) {
        super(accessToken);
        this.accessToken = accessToken;
    }

    public PushbulletProxy(String accessToken, String password) {
        super(accessToken, password);
        this.accessToken = accessToken;
    }

    @SuppressWarnings("unchecked")
    @Override
    public <TResult, TMessage extends HttpUriRequest> TResult executeRequest(Request<TResult, TMessage> apiRequest) {
        try {
            Type genericSuperclass = apiRequest.getClass().getGenericSuperclass();
            if (!(genericSuperclass instanceof ParameterizedType)) {
                genericSuperclass = ClassUtil.searchForSuperclassWithResponseType(apiRequest.getClass());
                if (genericSuperclass == null) return null;
            }
            ParameterizedType superType = (ParameterizedType) genericSuperclass;
            ParameterizedType parameterizedType = ClassUtil.searchForSuperclassWithHttpUriRequestType(apiRequest.getClass());
            Type responseType = superType.getActualTypeArguments()[0];
            Type messageType = parameterizedType.getActualTypeArguments()[1];
            Class<? extends HttpUriRequest> httpUriRequestClass = (Class<? extends HttpUriRequest>) messageType;
            URIBuilder builder = new URIBuilder(API_BASE_URL + apiRequest.getRelativePath());
            apiRequest.applyParameters(builder);
            Constructor<? extends HttpUriRequest> httpMessageConstructor = httpUriRequestClass.getConstructor(URI.class);
            URI uri = builder.build();

            RequestConfig defaultRequestConfig = RequestConfig.custom()
                    .setSocketTimeout(5000)
                    .setConnectTimeout(5000)
                    .setConnectionRequestTimeout(5000)
                    .setStaleConnectionCheckEnabled(true)
                    .build();

            RequestConfig requestConfig = RequestConfig.copy(defaultRequestConfig)
                    .setProxy(new HttpHost("127.0.0.1", 3128))
                    .build();

            HttpUriRequest httpMessage = httpMessageConstructor.newInstance(uri);
            ((HttpRequestBase)httpMessage).setConfig(requestConfig);

            httpMessage.addHeader(ACCESS_TOKEN_HEADER, accessToken);
            //Add body
            if (apiRequest instanceof EntityEnclosingRequest) {
                httpMessage.addHeader(HttpHeaders.CONTENT_TYPE, ContentType.APPLICATION_JSON.getMimeType());
                HttpEntityEnclosingRequestBase enclosingRequest = ((HttpEntityEnclosingRequestBase) httpMessage);
                EntityEnclosingRequest ownRequest = ((EntityEnclosingRequest) apiRequest);
                ownRequest.applyBody(getGson(), enclosingRequest);
            }
            //Execute request
            try (CloseableHttpClient client = HttpClients.createDefault(); CloseableHttpResponse response = client.execute(httpMessage)) {
                String responseString = EntityUtils.toString(response.getEntity());
                //this.getRateLimit() = Integer.valueOf(HttpUtil.getHeaderValue(response, RATELIMIT_LIMIT_HEADER, "0"));
                //this.getRateRemaining() = Integer.valueOf(HttpUtil.getHeaderValue(response, RATELIMIT_REMAINING_HEADER, "0"));
                //this.getResetTimestamp() = Long.valueOf(HttpUtil.getHeaderValue(response, RATELIMIT_RESET_HEADER, "0"));
                if (response.getStatusLine().getStatusCode() != HttpStatus.SC_OK) {
                    throw new PushbulletApiException(new PushbulletApiError("1", responseString, "1"));
                }
                return getGson().fromJson(responseString, responseType);
            }
        } catch (Exception e) {
            e.printStackTrace();
            throw new PushbulletApiException(new PushbulletApiError("1", "Exception", "1"));
        }
    }

    public UploadFile uploadFile(File file) {
        if(!file.exists()) throw new IllegalArgumentException("File not found.");
        long maxFileSize = getCurrentUser().getMaxUploadSize();
        if(file.length() > maxFileSize) throw new IllegalArgumentException("File is too big, max file size: " + maxFileSize);

        RequestFileUploadRequest fileUploadRequest = new RequestFileUploadRequest(file);

        UploadFile result = executeRequest(fileUploadRequest);
        AwsAuthData data = result.getData();
        HttpPost post = new HttpPost(result.getUploadUrl());

        RequestConfig defaultRequestConfig = RequestConfig.custom()
                .setSocketTimeout(5000)
                .setConnectTimeout(5000)
                .setConnectionRequestTimeout(5000)
                .setStaleConnectionCheckEnabled(true)
                .build();

        RequestConfig requestConfig = RequestConfig.copy(defaultRequestConfig)
                .setProxy(new HttpHost("127.0.0.1", 3128))
                .build();

        ((HttpRequestBase)post).setConfig(requestConfig);

        MultipartEntityBuilder builder = MultipartEntityBuilder.create()
                .addTextBody("awsaccesskeyid", data.getAwsAccessKeyId())
                .addTextBody("acl", data.getAccessControlList())
                .addTextBody("key", data.getKey())
                .addTextBody("signature", data.getSignature())
                .addTextBody("policy", data.getPolicy())
                .addTextBody("content-type", result.getFileType())
                .addBinaryBody("file", file);
        post.setEntity(builder.build());
        try (CloseableHttpClient client = HttpClients.createDefault(); CloseableHttpResponse response = client.execute(post)) {
            if(response.getStatusLine().getStatusCode() == HttpStatus.SC_NO_CONTENT) {
                return result;
            }
        }
        catch (IOException exception) {
            exception.printStackTrace();
        }
        return null;
    }
}
