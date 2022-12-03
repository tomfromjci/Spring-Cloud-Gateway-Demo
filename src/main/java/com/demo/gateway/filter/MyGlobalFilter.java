package com.demo.gateway.filter;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.demo.gateway.entity.AppInfo;
import com.demo.gateway.mapper.AppInfoMapper;
import com.demo.gateway.util.JSONUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.DigestUtils;
import org.springframework.util.MultiValueMap;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * 全局请求过滤器
 *
 * @author tom
 * @since v
 */
@Component
@Slf4j
public class MyGlobalFilter implements GlobalFilter, Ordered {

    private static final String APP_ID = "app-id";

    private static final String TIMESTAMP = "timestamp";

    private static final String AUTHORIZATION = "authorization";

    @Autowired
    private AppInfoMapper appInfoMapper;

    @Override

    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        log.info("received a request, uri path:{}", request.getURI().getPath());
        HttpMethod method = request.getMethod();
        HttpHeaders headers = request.getHeaders();
        List<String> appId = headers.get(APP_ID);
        List<String> timestamp = headers.get(TIMESTAMP);
        List<String> authorization = headers.get(AUTHORIZATION);

        // if any empty, then end current request
        if (appId == null || appId.isEmpty() || timestamp == null || timestamp.isEmpty() || authorization == null || authorization.isEmpty()) {
            // return 401 unauthorized
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return exchange.getResponse().setComplete();
        }

        // 检查时间戳，不可以小于当前时间一分钟，不可大于当前时间
        String timestampStr = timestamp.get(0);
        long timestampLong = Long.parseLong(timestampStr);
        long currentTime = System.currentTimeMillis();
        if (currentTime - timestampLong > 1000 * 60 || currentTime - timestampLong < 0) {
            log.info("时间戳校验失败，当前时间戳为：{}", currentTime);
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return exchange.getResponse().setComplete();
        }

        // check appId
        String appIdStr = appId.get(0);
        QueryWrapper<AppInfo> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("app_id", appIdStr);
        AppInfo appInfo = appInfoMapper.selectOne(queryWrapper);
        if (appInfo == null) {
            log.info("app id 校验失败，当前 app id[{}] 不存在", appId);
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return exchange.getResponse().setComplete();
        }

        // check authorization, signature = md5(requestParamJson&timestamp&appSecret)
        String signatureFromHeader = authorization.get(0);
        String appSecret = appInfo.getAppSecret();
        assert method != null;
        switch (method) {
            case GET:
            case DELETE:
                // 如果是 get 或 delete 请求，则取 query params
                MultiValueMap<String, String> queryParams = request.getQueryParams();
                // sort map
                SortedMap<String, String> sortedMap = new TreeMap<>();
                queryParams.forEach((key, value) -> sortedMap.put(key, value.get(0)));
                String requestParamJson = sortedMap.isEmpty() ? "" : JSON.toJSONString(sortedMap);

                String signatureWithParams = DigestUtils.md5DigestAsHex((requestParamJson + "&" + timestampStr + "&" + appSecret)
                        .getBytes(StandardCharsets.UTF_8));
                log.info("本次请求生成的签名为：{}", signatureWithParams);
                if (!signatureFromHeader.equals(signatureWithParams)) {
                    exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
                    return exchange.getResponse().setComplete();
                }

                return chain.filter(exchange);
            case POST:
            case PUT:
                // 如果是 post 或 put 请求，则取 body
                String body = exchange.getAttribute("cachedRequestBodyObject");
                JSONObject jsonObject = JSON.parseObject(body);
                String sortedBodyStr = JSONUtil.sortJsonObject(jsonObject);
                String sortedBodyJsonStr = sortedBodyStr == null || sortedBodyStr.isEmpty() ? "" : JSON.toJSONString(sortedBodyStr);
                String signatureWithBody = DigestUtils.md5DigestAsHex((sortedBodyJsonStr + "&" + timestampStr + "&" + appSecret)
                        .getBytes(StandardCharsets.UTF_8));
                if (!signatureFromHeader.equals(signatureWithBody)) {
                    log.info("签名校验失败，本次请求生成的签名为：{}", signatureWithBody);
                    exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
                    return exchange.getResponse().setComplete();
                }

                return chain.filter(exchange);
            default:
                return chain.filter(exchange);
        }
    }

    @Override
    public int getOrder() {
        return 0;
    }

    @Bean
    public RouteLocator customRouteLocator(RouteLocatorBuilder builder) {
        return builder.routes()
                // 给带 request body 的请求写一个路由
                .route("microServiceA-with-body", r -> r
                        .readBody(String.class, i -> true)
                        .and()
                        .asyncPredicate(serverWebExchange -> {
                            String path = serverWebExchange.getRequest().getPath().toString();
                            return Mono.just(path.contains("/open-api/microServiceA-test"));
                        })
                        .filters(gatewayFilterSpec -> gatewayFilterSpec.stripPrefix(2))
                        .uri("lb://microServiceA-test"))
                // 给不带 request body 的请求写一个路由
                .route("microServiceA-without-body", r -> r
                        .asyncPredicate(serverWebExchange -> {
                            String path = serverWebExchange.getRequest().getPath().toString();
                            return Mono.just(path.contains("/open-api/microServiceA-test"));
                        })
                        .filters(gatewayFilterSpec -> gatewayFilterSpec.stripPrefix(2))
                        .uri("lb://microServiceA-test"))
                .route("microServiceB-with-body", r -> r
                        .readBody(String.class, i -> true)
                        .and()
                        .asyncPredicate(serverWebExchange -> {
                            String path = serverWebExchange.getRequest().getPath().toString();
                            return Mono.just(path.contains("/open-api/microServiceB-test"));
                        })
                        .filters(gatewayFilterSpec -> gatewayFilterSpec.stripPrefix(2))
                        .uri("lb://microServiceB-test"))
                .route("microServiceB-without-body", r -> r
                        .asyncPredicate(serverWebExchange -> {
                            String path = serverWebExchange.getRequest().getPath().toString();
                            return Mono.just(path.contains("/open-api/microServiceB-test"));
                        })
                        .filters(gatewayFilterSpec -> gatewayFilterSpec.stripPrefix(2))
                        .uri("lb://microServiceB-test"))
                .build();
    }

}
