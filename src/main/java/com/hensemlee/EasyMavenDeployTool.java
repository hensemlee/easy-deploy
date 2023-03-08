package com.hensemlee;

import cn.hutool.core.collection.CollUtil;
import com.google.common.io.Files;
import com.hensemlee.util.DeployUtils;
import lombok.Data;
import org.apache.maven.shared.invoker.DefaultInvocationRequest;
import org.apache.maven.shared.invoker.DefaultInvoker;
import org.apache.maven.shared.invoker.InvocationRequest;
import org.apache.maven.shared.invoker.Invoker;

import java.io.File;
import java.util.*;
import java.util.stream.Collectors;

/**
 * @author hensemlee
 * @create 2023/3/4 13:33
 */
public class EasyMavenDeployTool {

    public static void main(String[] args) {
        // 从命令行参数获取要deploy的项目
        if (args.length == 0) {
            System.out.println("\u001B[31mUsage: easy-deploy project_name1 project_name2 ...\u001B[0m");
            System.exit(1);
        }
        List<String> projects = Arrays.stream(args).collect(Collectors.toList());
        if (CollUtil.isEmpty(projects)) {
            System.out.println("\u001B[31mUsage: easy-deploy project_name1 project_name2 ...\u001B[0m");
            System.exit(1);
        }
        // 模糊匹配项目
        List<String> candidatePomFiles = matchProject(projects);
        deployFile(candidatePomFiles);
        System.out.println("\u001B[32m>>>>>>> deploy完成! >>>>>>>\u001B[0m");
        System.exit(1);
    }

    /**
     * 模糊匹配项目
     *
     * @param projects
     * @return 匹配到的项目
     */
    private static List<String> matchProject(List<String> projects) {
        Map<String, String> absolutePathByArtifactId = findAllMavenProjects();
        if (CollUtil.isEmpty(absolutePathByArtifactId)) {
            System.out.println("\u001B[33mNo Maven project found in target directory.\u001B[0m");
            System.exit(1);
        }
        Set<String> candidateProjects = new HashSet<>();
        Map<String, Set<String>> prompt = new HashMap<>();
        projects.forEach(project -> {
            Set<String> promptSet = new HashSet<>();
            absolutePathByArtifactId.forEach((k, v) -> {
                if (project.startsWith("'") && project.endsWith("'")) {
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
        return sortHelpers.stream().sorted(Comparator.comparing(SortHelper::getSort)).map(helper -> {
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
                        "\u001B[33mproject_name【" + k + "】 matched multi project: " + Arrays.asList(v.toArray())
                                + " it may casue deploy failure, please comfirm and try again\u001B[0m");
                System.exit(1);
            }
        });
    }

    private static Map<String, String> findAllMavenProjects() {
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

    private static void deployFile(List<String> candidatePomFiles) {
        if (CollUtil.isEmpty(candidatePomFiles)) {
            System.out.println("\u001B[33mNo POM need to deploy!\u001B[0m");
            System.exit(1);
        }
        System.out.println("\u001B[32m>>>>>>> start to deploy " + candidatePomFiles.size() + "projects below sequencelly: >>>>>>>\u001B[0m");
        candidatePomFiles.forEach(System.out::println);
        List<InvocationRequest> requests = candidatePomFiles.stream().map(pom -> {
            InvocationRequest request = new DefaultInvocationRequest();
            request.setPomFile(new File(pom));
            request.setGoals(Arrays.asList("clean", "package", "install", "deploy"));
            return request;
        }).collect(Collectors.toList());
        Invoker invoker = new DefaultInvoker();
        invoker.setInputStream(System.in);
        invoker.setMavenHome(new File(System.getenv("M2_HOME")));
        requests.forEach(request -> {
            try {
                System.out.println("\u001B[32m>>>>>>> start to deploy " + request.getPomFile() + " \u001B[0m");
                invoker.execute(request);
                System.out.println("\u001B[32m>>>>>>> " + request.getPomFile() + " deploy over !\u001B[0m");
            } catch (Throwable e) {
                e.printStackTrace();
                System.exit(1);
            }
        });
        System.out.println("\u001B[32m>>>>>>> all deployments complete >>>>>>>\u001B[0m");
        System.exit(1);
    }

    @Data
    private static class SortHelper {
        private int sort;
        private String name;
    }
}
