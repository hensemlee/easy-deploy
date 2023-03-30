package com.hensemlee;

import static com.hensemlee.contants.Constants.PARENT_PROJECT_NAME;
import static com.hensemlee.contants.Constants.RELEASE_PATTERN;

import cn.hutool.core.collection.CollUtil;
import com.google.common.collect.Lists;
import com.google.common.io.Files;
import com.hensemlee.bean.SortHelper;
import com.hensemlee.bean.req.EffectiveMavenReq;
import com.hensemlee.exception.EasyDeployException;
import com.hensemlee.util.ArtifactQueryUtils;
import com.hensemlee.util.DeployUtils;
import com.hensemlee.util.POMUtils;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Scanner;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;
import org.apache.maven.shared.invoker.DefaultInvocationRequest;
import org.apache.maven.shared.invoker.DefaultInvoker;
import org.apache.maven.shared.invoker.InvocationRequest;
import org.apache.maven.shared.invoker.InvocationResult;
import org.apache.maven.shared.invoker.Invoker;
import org.apache.maven.shared.invoker.MavenInvocationException;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;
import org.dom4j.Document;
import org.dom4j.DocumentException;
import org.dom4j.io.SAXReader;

/**
 * @author hensemlee
 * @create 2023/3/4 13:33
 */
public class EasyMavenDeployTool {

    private static final String ALL_DEPLOY_FLAG = "ALL";
    private static final String FIX_FLAG = "FIX";

    private static final String LAUNCH_FLAG = "LAUNCH";

    private static int INCREMENT = 1;

    private static final int MAJOR_VERSION_THRESHOLD = 10;
    private static final int MINOR_VERSION_THRESHOLD = 30;
    private static final int PATCH_VERSION_THRESHOLD = 60;

    public static void main(String[] args) throws XmlPullParserException, IOException {
        // 从命令行参数获取要deploy的项目
        if (args.length == 0) {
            System.out.println(
                "\u001B[31mUsage: easy-deploy project_name1 project_name2 ...\u001B[0m");
            System.out.println(
                "\u001B[31mUsage: (发布SNAPSHOT或RELEASE到远程maven仓库)\u001B[0m");
            System.out.println(
                "\u001B[31mUsage: easy-deploy fix project_name1 project_name2 ...\u001B[0m");
            System.out.println(
                "\u001B[31mUsage: (解决依赖引用不到的情况)\u001B[0m");
            System.out.println(
                "\u001B[31mUsage: easy-deploy all \u001B[0m");
            System.out.println(
                "\u001B[31mUsage: (发布所有需要deploy的项目到远程maven仓库)\u001B[0m");
            System.out.println(
                "\u001B[31mUsage: easy-deploy launch \u001B[0m");
            System.out.println(
                "\u001B[31mUsage: (一键自动修改SNAPSHOT成RELEASE, 并发布远程maven仓库，准备部署上线)\u001B[0m");
            System.exit(1);
        }
        List<String> projects = Arrays.stream(args).filter(StringUtils::isNotBlank).collect(Collectors.toList());
        if (CollUtil.isEmpty(projects)) {
            System.out.println(
                "\u001B[31mUsage: easy-deploy project_name1 project_name2 ...\u001B[0m");
            System.exit(1);
        }

        if (projects.size() == 1 && LAUNCH_FLAG.equalsIgnoreCase(projects.get(0))) {
            Map<String, String> absolutePathByArtifactId = findAllMavenProjects();
            String parentPOM = absolutePathByArtifactId.get(PARENT_PROJECT_NAME);
            File pomFile = new File(parentPOM);
            SAXReader reader = new SAXReader();
            Document document;
            try {
                document = reader.read(pomFile);
            } catch (DocumentException e) {
                throw new EasyDeployException("Error reading pom.xml: " + e.getMessage());
            }
            String oldVersion = document.getRootElement().element("version").getText();
            String artifactId = document.getRootElement().element("artifactId").getText();
            if (Pattern.matches(RELEASE_PATTERN, oldVersion)) {
                System.out.println("\u001B[31m>>>>>>> 当前Parent已是RELEASE版本，是否继续？\u001B[0m");
                Scanner scanner = new Scanner(System.in);
                System.out.print("\u001B[31m请输入 'y' 继续执行，或者输入其他字符结束执行：\u001B[0m");
                String input = scanner.next();
                if (!input.equalsIgnoreCase("y")) {
                    System.exit(1);
                }
            }
            String currentVersion = ArtifactQueryUtils.queryCandidateVersion(buildReq(artifactId));
            List<String> splits = Arrays.stream(currentVersion.split("\\."))
                .collect(Collectors.toList());
            if (CollUtil.isEmpty(splits)) {
                throw new EasyDeployException("查询Parent最新版本失败");
            }
            List<String> candidatePomFiles = Lists.newArrayList();
            String finalNewVersion;
            candidatePomFiles.add(parentPOM);
            System.out.println("\u001B[32m>>>>>>> start to update parent pom release version !\u001B[0m");
            try {
                finalNewVersion = generateFinalNewVersion(splits);
                POMUtils.updateParentPomVersion(parentPOM, oldVersion, finalNewVersion);
            } catch (Exception e) {
                throw new EasyDeployException("更新Parent工程POM失败");
            }
            System.out.println("\u001B[32m>>>>>>> update parent pom release version successfully !\u001B[0m");
            System.out.println("\u001B[32m>>>>>>> start to update other pom release version  !\u001B[0m");
            absolutePathByArtifactId.values().forEach(pom -> {
                try {
                    POMUtils.updatePomVersion(pom, oldVersion, finalNewVersion, candidatePomFiles);
                } catch (Exception e) {
                    throw new EasyDeployException("更新业务子工程POM失败");
                }
            });
            System.out.println("\u001B[32m>>>>>>> other pom release version update successfully !\u001B[0m");
            deployFile(candidatePomFiles);
        }

        // 模糊匹配项目
        if (projects.size() > 1 && ALL_DEPLOY_FLAG.equalsIgnoreCase(projects.get(0))) {
            projects = DeployUtils.getAllNeedDeployedProjects().stream()
                .map(project -> "[" + project + "]").collect(Collectors.toList());
        }


        if (!CollUtil.isEmpty(projects) && !FIX_FLAG.equalsIgnoreCase(projects.get(0))) {
            List<String> candidatePomFiles = matchProject(projects);
            deployFile(candidatePomFiles);
            System.out.println("\u001B[32m>>>>>>> deploy完成! >>>>>>>\u001B[0m");
            System.exit(1);
        }

        if (!CollUtil.isEmpty(projects) && FIX_FLAG.equalsIgnoreCase(projects.get(0))) {
            projects = projects.subList(1, projects.size());
            Map<String, String> absolutePathByArtifactId = findAllMavenProjects();
            Set<String> candidateProjects = new HashSet<>();
            List<String> allNeedDeployedProjects = DeployUtils.getAllNeedDeployedProjects();
            Map<String, Set<String>> prompt = new HashMap<>();
            projects.forEach(project -> {
                Set<String> promptSet = new HashSet<>();
                absolutePathByArtifactId.forEach((k, v) -> {
                    if (project.startsWith("[") && project.endsWith("]")) {
                        String format = project.substring(1, project.length() - 1);
                        if (k.equals(format)) {
                            candidateProjects.add(k);
                            promptSet.add(k);
                        }
                    } else if ((k.contains(project) || k.equals(project))
                        && !allNeedDeployedProjects.contains(k)) {
                        candidateProjects.add(k);
                        promptSet.add(k);
                    }
                });
                prompt.put(project, promptSet);
            });
            promotion(prompt);

            Invoker invoker = getInvoker();

            candidateProjects.forEach(candidate -> {
                InvocationRequest request = new DefaultInvocationRequest();
                request.setPomFile(new File(absolutePathByArtifactId.get(candidate)));
                request.setGoals(Collections.singletonList("idea:idea"));
                try {
                    System.out.println("\u001B[32m>>>>>>> start to fix dependency " + candidate
                        + " >>>>>>> \u001B[0m");
                    invoker.execute(request);
                    System.out.println("\u001B[32m>>>>>>> " + request.getPomFile()
                        + " dependency fix successfully ! >>>>>>> \u001B[0m");
                } catch (MavenInvocationException e) {
                    System.out.println("\u001B[31m>>>>>>> " + request.getPomFile()
                        + " dependency fix failure ! >>>>>>> \u001B[0m");
                }
            });
            System.exit(1);
        }
    }

    private static Invoker getInvoker() {
        Invoker invoker = new DefaultInvoker();
        invoker.setInputStream(System.in);
        invoker.setMavenHome(new File(System.getenv("M2_HOME")));
        return invoker;
    }

    /**
     * 模糊匹配项目
     *
     * @param projects
     * @return 匹配到的项目
     */
    private static List<String> matchProject(List<String> projects) {
        Map<String, String> absolutePathByArtifactId = findAllNeedDeployedPomFiles();
        if (CollUtil.isEmpty(absolutePathByArtifactId)) {
            System.out.println("\u001B[33mNo Maven project found in target directory.\u001B[0m");
            System.exit(1);
        }
        Set<String> candidateProjects = new HashSet<>();
        Map<String, Set<String>> prompt = new HashMap<>();
        projects.forEach(project -> {
            Set<String> promptSet = new HashSet<>();
            absolutePathByArtifactId.forEach((k, v) -> {
                if (project.startsWith("[") && project.endsWith("]")) {
                    String format = project.substring(1, project.length() - 1);
                    if (k.equals(format)) {
                        candidateProjects.add(k);
                        promptSet.add(k);
                    }
                } else if (k.contains(project) || k.equals(project)) {
                    candidateProjects.add(k);
                    promptSet.add(k);
                }
            });
            prompt.put(project, promptSet);
        });
        promotion(prompt);
        List<SortHelper> sortHelpers = new ArrayList<>();
        candidateProjects.forEach(c -> {
            if (DeployUtils.contains(c)) {
                SortHelper sortHelper = new SortHelper();
                sortHelper.setName(c);
                sortHelper.setSort(DeployUtils.indexOf(c));
                sortHelpers.add(sortHelper);
            }
        });
        return sortHelpers.stream().sorted(Comparator.comparing(SortHelper::getSort))
            .map(helper -> {
                if (Objects.nonNull(absolutePathByArtifactId.get(helper.getName()))) {
                    return absolutePathByArtifactId.get(helper.getName());
                }
                return null;
            }).filter(Objects::nonNull).collect(Collectors.toList());
    }

    private static void promotion(Map<String, Set<String>> prompt) {
        prompt.forEach((k, v) -> {
            if (v.size() > 1) {
                System.out.println(
                    "\u001B[33mproject_name【" + k + "】 matched multi project: " + Arrays.asList(
                        v.toArray())
                        + " it may casue deploy failure, please comfirm and try again\u001B[0m");
                System.exit(1);
            }
        });
    }

    private static Map<String, String> findAllNeedDeployedPomFiles() {
        Map<String, String> absolutePathByArtifactId = new HashMap<>(64);
        File rootDir = new File(System.getenv("TARGET_PROJECT_FOLDER"));
        Iterator<File> iterator = Files.fileTraverser().depthFirstPreOrder(rootDir).iterator();
        while (iterator.hasNext()) {
            File file = iterator.next();
            if (file.isFile() && file.getName().equals("pom.xml")) {
                String parentName = file.getParentFile().getName();
                if (DeployUtils.contains(parentName)) {
                    absolutePathByArtifactId.put(parentName, file.getAbsolutePath());
                }
            }
        }
        return absolutePathByArtifactId;
    }

    private static Map<String, String> findAllMavenProjects() {
        Map<String, String> absolutePathByArtifactId = new HashMap<>(64);
        File rootDir = new File(System.getenv("TARGET_PROJECT_FOLDER"));
        Iterator<File> iterator = Files.fileTraverser().depthFirstPreOrder(rootDir).iterator();
        while (iterator.hasNext()) {
            File file = iterator.next();
            if (file.isFile() && file.getName().equals("pom.xml")) {
                String parentName = file.getParentFile().getName();
                absolutePathByArtifactId.put(parentName, file.getAbsolutePath());
            }
        }
        return absolutePathByArtifactId;
    }

    private static void deployFile(List<String> candidatePomFiles) {
        if (CollUtil.isEmpty(candidatePomFiles)) {
            System.out.println("\u001B[33mNo POM need to deploy!\u001B[0m");
            System.exit(1);
        }
        System.out.println("\u001B[32m>>>>>>> start to deploy " + candidatePomFiles.size()
            + " projects below sequencelly: >>>>>>>\u001B[0m");
        candidatePomFiles.forEach(System.out::println);
        List<InvocationRequest> requests = candidatePomFiles.stream().map(pom -> {
            InvocationRequest request = new DefaultInvocationRequest();
            request.setPomFile(new File(pom));
            request.setGoals(Arrays.asList("clean", "package", "install", "deploy"));
            return request;
        }).collect(Collectors.toList());
        Invoker invoker = getInvoker();
        List<String> deployFailureProjects = new ArrayList<>();
        requests.forEach(request -> {
            try {
                System.out.println(
                    "\u001B[32m>>>>>>> start to deploy " + request.getPomFile() + " \u001B[0m");
                InvocationResult result = invoker.execute(request);
                if (result.getExitCode() != 0) {
                    System.out.println(
                        "\u001B[31m>>>>>>> " + request.getPomFile() + " deploy failure !\u001B[0m");
                    deployFailureProjects.add(request.getPomFile().toString());
                } else {
                    System.out.println("\u001B[32m>>>>>>> " + request.getPomFile()
                        + " deploy successfully !\u001B[0m");
                }
            } catch (Exception e) {
                deployFailureProjects.add(request.getPomFile().toString());
            }
        });
        System.out.println("\u001B[32m>>>>>>> deploy情况如下: >>>>>>>\u001B[0m");
        if (deployFailureProjects.isEmpty()) {
            System.out.println(
                "\u001B[32m>>>>>>> all projects deploy successfully >>>>>>>\u001B[0m");
        } else {
            deployFailureProjects.forEach(deploy -> System.out.println(
                "\u001B[31m>>>>>>> " + deploy + " deploy failure !\u001B[0m"));
        }
        System.exit(1);
    }
    private static String generateFinalNewVersion(List<String> splits) {
        String finalNewVersion;
        int patchVersion = Integer.parseInt(splits.get(splits.size() - 2));
        int newPatchVersion = patchVersion + INCREMENT;
        if (patchVersion > PATCH_VERSION_THRESHOLD) {
            int minorVersion = Integer.parseInt(splits.get(splits.size() - 3));
            int newMinorVersion = minorVersion + INCREMENT;
            if (newMinorVersion > MINOR_VERSION_THRESHOLD) {
                int majorVersion = Integer.parseInt(splits.get(splits.size() - 4));
                int newMajorVersion = majorVersion + INCREMENT;
                if (newMajorVersion > MAJOR_VERSION_THRESHOLD) {
                    throw new EasyDeployException("版本号超过设定的阈值");
                }
                splits.set(splits.size() - 4, String.valueOf(newMajorVersion));
            }
            splits.set(splits.size() - 3, String.valueOf(newMinorVersion));
        }
        splits.set(splits.size() - 2, String.valueOf(newPatchVersion));
        finalNewVersion = String.join(".", splits);
        return finalNewVersion;
    }

    private static EffectiveMavenReq buildReq(String artifactId) {
        return EffectiveMavenReq.builder()
            .repos(Lists.newArrayList("libs-release-local", "libs-snapshot-local"))
            .artifactId(artifactId)
            .type("pom")
            .updated("2023-01-01")
            .exact(false)
            .build();
    }
}
