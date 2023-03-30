package com.hensemlee.util;

import static com.hensemlee.contants.Constants.PARENT_PROJECT_NAME;
import static com.hensemlee.contants.Constants.PATH_DELIMITER;
import static com.hensemlee.contants.Constants.RELEASE_PATTERN;
import static com.hensemlee.contants.Constants.X_JFROG_ART_API_HEADER;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.http.ContentType;
import cn.hutool.http.Header;
import cn.hutool.http.HttpRequest;
import com.alibaba.fastjson.JSON;
import com.hensemlee.bean.ArtifactItem;
import com.hensemlee.bean.req.EffectiveMavenReq;
import com.hensemlee.bean.resp.EffectiveMavenRes;
import com.hensemlee.bean.resp.EffectiveMavenRes.ArtifactVo;
import com.hensemlee.bean.resp.JfrogAqlResult;
import com.hensemlee.exception.EasyDeployException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.jfrog.artifactory.client.aql.AqlItem;
import org.jfrog.artifactory.client.aql.AqlQueryBuilder;

@Slf4j
public class ArtifactQueryUtils {

    public static String queryCandidateVersion(EffectiveMavenReq req) {
        String aql = buildAql(req);
        JfrogAqlResult jfrogAqlResult;
        try {
            String body = HttpRequest
                .post(DeployUtils.getJfrogArtifactoryURL())
                .header(X_JFROG_ART_API_HEADER, DeployUtils.getJfrogArtifactoryApiKey())
                .header(Header.CONTENT_TYPE, ContentType.TEXT_PLAIN.toString())
                .body(aql)
                .execute().body();
            jfrogAqlResult = JSON.parseObject(body, JfrogAqlResult.class);
        } catch (Exception e) {
            log.error("jrog open api searchByAql 调用出错", e);
            throw new EasyDeployException(e.getMessage());
        }
        if (Objects.isNull(jfrogAqlResult) || Objects.isNull(jfrogAqlResult.getResults())) {
            throw new EasyDeployException("未找到Parent最新版本");
        }
        return doQueryCandidateVersion(jfrogAqlResult.getResults());
    }

    private static String buildAql(EffectiveMavenReq effectiveMavenReq) {
        AqlQueryBuilder aqlQueryBuilder = new AqlQueryBuilder();
        List<AqlItem> aqlItems = new ArrayList<>();
        // repos
        List<String> repos = effectiveMavenReq.getRepos();
        if (CollUtil.isEmpty(repos)) {
            repos = Arrays.asList("libs-release-local", "libs-snapshot-local");
        }
        AqlItem[] repoConItems = repos.stream().map((repo -> AqlItem.aqlItem("repo", repo)))
            .collect(Collectors.toList())
            .toArray(aqlItems.toArray(new AqlItem[0]));
        aqlItems.add(AqlItem.or(repoConItems));
        // path
        // "path": "com/tezign/intelligence/api-message/3.7.41.RELEASE",
        String searchGroupId = effectiveMavenReq.getGroupId();
        String searchArtifactId = effectiveMavenReq.getArtifactId();
        boolean searchExact = effectiveMavenReq.isExact();
        String pathParam = searchArtifactId;
        if (searchExact) {
            pathParam = PATH_DELIMITER + pathParam;
            if (StringUtils.isNotBlank(searchGroupId)) {
                pathParam = searchGroupId.replace(".", PATH_DELIMITER) + pathParam;
            } else {
                pathParam = "*" + pathParam;
            }
            pathParam = pathParam + PATH_DELIMITER + "*.*";
        } else {
            if (StringUtils.isNotBlank(searchGroupId)) {
                pathParam = searchGroupId.replace(".", PATH_DELIMITER) + "*" + pathParam;
            }
            pathParam = "*" + pathParam + "*.*";
        }
        AqlItem pathAqlItem = AqlItem.aqlItem("path", AqlItem.aqlItem("$match", pathParam));
        aqlItems.add(pathAqlItem);
        // name
        //  "name": "api-message-3.7.41.RELEASE.jar",
        String type = effectiveMavenReq.getType();
        if (Objects.isNull(type) || StrUtil.isBlank(type)) {
            type = EffectiveMavenReq.TypeEnum.JAR.getType();
        }
        String nameParam;
        if (searchExact) {
            nameParam = String.format("%s-*.%s", searchArtifactId, type);
        } else {
            nameParam = String.format("*%s*.%s", searchArtifactId, type);
        }
        AqlItem nameAqlItem = AqlItem.aqlItem("name", AqlItem.aqlItem("$match", nameParam));
        aqlItems.add(nameAqlItem);
        // updated
        String updatedParam = effectiveMavenReq.getUpdated();
        if (Objects.isNull(updatedParam) || StrUtil.isBlank(updatedParam)) {
            updatedParam = "2023-01-01";
        }
        AqlItem updatedAqlItem = AqlItem.aqlItem("updated", AqlItem.aqlItem("$gt", updatedParam));
        aqlItems.add(updatedAqlItem);
        // aqlStr
        aqlQueryBuilder.elements(aqlItems.toArray(new AqlItem[0]));
        String aql = aqlQueryBuilder.build();
        log.info("当前请求对应的 aql 为\n{}", aql);
        return aql;
    }

    private static String doQueryCandidateVersion(List<ArtifactItem> artifactItems) {
        // 返回对象组装
        // 去重
        // 设置gav等信息
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-ddHH:mm:ss.SSS");
        List<EffectiveMavenRes.ArtifactVo> tmpArtifactVos = artifactItems.stream()
            .map(artifactItem -> {
                EffectiveMavenRes.ArtifactVo artifactVo = new EffectiveMavenRes.ArtifactVo();
                BeanUtil.copyProperties(artifactItem, artifactVo);
                // 时间
                Date updatedDate = null;
                String updatedStr = artifactItem.getUpdated().replace("T", "").replace("Z", "");
                try {
                    updatedDate = format.parse(updatedStr);
                } catch (ParseException e) {
                    log.warn("日期解析出错", e);
                }
                // groupId artifactId
                String path = artifactItem.getPath();
                String[] pathSplits = path.split("/");
                String artifactId = pathSplits[pathSplits.length - 2];
                String[] groupIdSplits = Arrays.copyOfRange(pathSplits, 0, pathSplits.length - 2);
                String groupId = StringUtils.join(groupIdSplits, ".");
                // version
                String version = pathSplits[pathSplits.length - 1];
                // snapshot
                boolean snapshot = version.endsWith("-SNAPSHOT");

                artifactVo.setUpdatedDate(updatedDate);
                artifactVo.setGroupId(groupId);
                artifactVo.setArtifactId(artifactId);
                artifactVo.setVersion(version);
                artifactVo.setSnapshot(snapshot);
                return artifactVo;
            })
            // 排序，最新打的包放前面
            // 返回 > 0，则 a 排在前面；返回小于 0，则 a 排在后面
            .sorted((a, b) -> b.getUpdatedDate().compareTo(a.getUpdatedDate()))
            .collect(Collectors.toList());

        // 去重，同一个snapshot的artifactId可能有多次发布，取最新的
        List<EffectiveMavenRes.ArtifactVo> artifactVos = new ArrayList<>();
        tmpArtifactVos.forEach(tmpArtifactVo -> {
            if(artifactVos.contains(tmpArtifactVo)) {
                return;
            }
            artifactVos.add(tmpArtifactVo);
        });

        Optional<ArtifactVo> optional = artifactVos.stream().filter(artifactVo -> {
            boolean condition1 = PARENT_PROJECT_NAME.equals(artifactVo.getArtifactId());
            boolean condition2 = Pattern.matches(RELEASE_PATTERN, artifactVo.getVersion());
            return condition1 && condition2;
        }).sorted(Comparator.comparing(ArtifactVo::getCreated).reversed()).findFirst();
        if (optional.isPresent()) {
            log.info("找到对应的Parent最新版本为：{}", optional.get().getVersion());
            return optional.get().getVersion();
        }
        throw new EasyDeployException("没有找到对应的最新版本");
    }
}