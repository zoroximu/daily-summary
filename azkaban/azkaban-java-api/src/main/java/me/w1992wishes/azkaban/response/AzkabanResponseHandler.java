package me.w1992wishes.azkaban.response;

import com.alibaba.fastjson.JSONObject;
import org.apache.http.HttpEntity;
import org.apache.http.client.fluent.Request;
import org.apache.http.client.fluent.Response;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

/**
 * 响应处理器
 *
 * @author Administrator
 */
public class AzkabanResponseHandler {
    private static Logger log = LoggerFactory.getLogger(AzkabanResponseHandler.class);

    public static <T extends AzkabanBaseResponse> T handle(Request request, Class<T> tClass) {
        T response = null;
        try {
            Response res = request.execute();
            HttpEntity entity = res.returnResponse().getEntity();
            response = handle(entity, tClass);
        } catch (Exception e) {
            try {
                response = tClass.newInstance();
                response.setStatus(T.ERROR);
                response.setError(e.getMessage());
                response.correction();
            } catch (Exception ea) {
                log.warn(ea.getMessage());
            }
        }
        return response;
    }

    public static AzkabanBaseResponse handle(Request request) {
        return handle(request, AzkabanBaseResponse.class);
    }


    public static AzkabanBaseResponse handle(String content) {
        return handle(content, AzkabanBaseResponse.class);
    }

    public static <T extends AzkabanBaseResponse> T handle(HttpEntity entity, Class<T> tClass) {
        T response = null;
        try {
            String content = EntityUtils.toString(entity);
            response = handle(content, tClass);
        } catch (Exception e) {
            log.warn(e.getMessage());
        }
        return response;
    }

    public static <T extends AzkabanBaseResponse> T handle(String content, Class<T> tClass) {
        T response = null;
        try {
            response = JSONObject.parseObject(content, tClass);
            if (Objects.nonNull(response.getError())) {
                response.setStatus(T.ERROR);
            }
            response.correction();
        } catch (Exception e) {
            try {
                response = tClass.newInstance();
                response.setStatus(T.ERROR);
                response.setError(content);
                response.correction();
            } catch (Exception ea) {
                log.warn(ea.getMessage());
            }
        }
        return response;
    }
}
