package com.hensemlee.util;

import cn.hutool.http.HttpRequest;
import com.alibaba.fastjson.JSON;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.hensemlee.bean.resp.CicdProject;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import static com.hensemlee.contants.Constants.CICD_HOST;

/**
 * @author hensemlee
 * @owner lijun
 * @team Research and Development Efficiency.
 * @since 2023-06-14 16:18
 */
@Slf4j
public class CicdUtils {
    public static List<CicdProject> retrieveAllCicdProjects(int current, String token) {
        Map<String, Object> query = Maps.newHashMap();
        query.put("current", current);
        query.put("language", "java");
        query.put("pagesize", 20);
        String body;
        try {
            body = HttpRequest.get(CICD_HOST)
                    .header("cookie", "token=" + token)
                    .form(query)
                    .execute()
                    .body();
            if (Objects.nonNull(body) && Objects.nonNull(JSON.parseObject(body))) {
                String listStr = JSON.parseObject(body).getString("list");
                if (Objects.nonNull(listStr)) {
                    List<CicdProject> projects = JSON.parseArray(listStr, CicdProject.class);
                    return projects;
                }
            }
        } catch (Exception e) {
            log.error("retrieveAllCicdProjects error occurred: ", e);
        }
        return Lists.newArrayList();
    }
}
