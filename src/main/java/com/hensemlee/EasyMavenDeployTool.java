package com.hensemlee;

import static com.hensemlee.contants.Constants.ALL_DEPLOY_FLAG;
import static com.hensemlee.contants.Constants.API_DELAYED_PROJECT_NAME;
import static com.hensemlee.contants.Constants.CHAT_FLAG;
import static com.hensemlee.contants.Constants.DEFAULT_API_DELAYED_VERSION;
import static com.hensemlee.contants.Constants.DEFAULT_REVISION;
import static com.hensemlee.contants.Constants.DEV_FLAG;
import static com.hensemlee.contants.Constants.FIX_FLAG;
import static com.hensemlee.contants.Constants.INCREMENT;
import static com.hensemlee.contants.Constants.MAJOR_VERSION_THRESHOLD;
import static com.hensemlee.contants.Constants.MINOR_VERSION_THRESHOLD;
import static com.hensemlee.contants.Constants.OPENAI_API_HOST;
import static com.hensemlee.contants.Constants.OPENAI_API_HOST_DEFAULT_VALUE;
import static com.hensemlee.contants.Constants.OPENAI_API_KEY;
import static com.hensemlee.contants.Constants.PARENT_PROJECT_NAME;
import static com.hensemlee.contants.Constants.PATCH_VERSION_THRESHOLD;
import static com.hensemlee.contants.Constants.PRD_FLAG;
import static com.hensemlee.contants.Constants.SNAPSHOT_SUFFIX;
import static com.hensemlee.contants.Constants.TARGET_PROJECT_FOLDER;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.google.common.collect.Lists;
import com.google.common.io.Files;
import com.hensemlee.bean.FixedSizeQueue;
import com.hensemlee.bean.SortHelper;
import com.hensemlee.bean.req.EffectiveMavenReq;
import com.hensemlee.exception.EasyDeployException;
import com.hensemlee.util.DeployUtils;
import com.hensemlee.util.GitUtils;
import com.hensemlee.util.POMUtils;
import com.plexpt.chatgpt.ChatGPTStream;
import com.plexpt.chatgpt.entity.chat.ChatCompletion;
import com.plexpt.chatgpt.entity.chat.Message;
import com.plexpt.chatgpt.listener.ConsoleStreamListener;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Scanner;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.LongAccumulator;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;
import org.dom4j.Document;
import org.dom4j.Element;

/**
 * @author hensemlee
 * @create 2023/3/4 13:33
 */
public class EasyMavenDeployTool {
    private static Map<String, String> absolutePathByArtifactId = new HashMap<>(64);
    private static Map<String, String> artifactIdByAbsolutePath = new HashMap<>(64);

    private static LongAccumulator accumulator = new LongAccumulator(Long::sum, 0L);

    private static FixedSizeQueue<Message> messages = new FixedSizeQueue<>(10);

	private static AtomicBoolean deployFail = new AtomicBoolean(false);

    static {
        fillMaps();
    }

    public static void main(String[] args)
        throws IOException, InterruptedException {
        // 从命令行参数获取要deploy的项目
        if (args.length == 0) {
            System.out.println(
                "\u001B[32mUsage: easy-deploy project_name1 project_name2 ...\u001B[0m");
            System.out.println(
                "\u001B[32m       (发布SNAPSHOT或RELEASE到远程maven仓库)\u001B[0m");
            System.out.println(
                "\u001B[32mUsage: easy-deploy fix project_name1 project_name2 ...\u001B[0m");
            System.out.println(
                "\u001B[32m       (解决依赖引用不到的情况)\u001B[0m");
            System.out.println(
                "\u001B[32mUsage: easy-deploy all \u001B[0m");
            System.out.println(
                "\u001B[32m       (发布所有需要deploy的项目到远程maven仓库)\u001B[0m");
            System.out.println(
                "\u001B[32mUsage: easy-deploy dev \u001B[0m");
            System.out.println(
                "\u001B[32m       (开发时，merge了origin/master或改了api-xxxx需要一键deploy的情况)\u001B[0m");
            System.out.println(
                "\u001B[32mUsage: easy-deploy prd \u001B[0m");
            System.out.println(
                "\u001B[32m       (一键自动修改SNAPSHOT成RELEASE, 并发布远程maven仓库、提交代码，准备部署上线)\u001B[0m");
            System.out.println(
                "\u001B[32mUsage: easy-deploy chat xxxxxxx \u001B[0m");
            System.out.println(
                "\u001B[32m       (在IDE里开发时，随时可在命令行发起ChatGPT提问&聊天)\u001B[0m");
            System.exit(1);
        }
        List<String> projects = Arrays.stream(args).filter(StringUtils::isNotBlank).collect(Collectors.toList());
        if (CollUtil.isEmpty(projects)) {
            System.out.println(
                "\u001B[31mUsage: easy-deploy project_name1 project_name2 ...\u001B[0m");
            System.exit(1);
        }

        if (projects.size() == 1 && PRD_FLAG.equalsIgnoreCase(projects.get(0))) {

			Document parentDocument = POMUtils.getParentDocument();
			Element properties = parentDocument.getRootElement().element("properties");
			Element revision = properties.element("revision");
			Element versionApiDelayed = properties.element("version.api-delayed");

			String tempReversion = revision.getText();
			String tempVersionApiDelayed = versionApiDelayed.getText();

			Document apiDelayedDocument = POMUtils.getApiDelayedDocument();
			Element apiProperties = apiDelayedDocument.getRootElement().element("properties");
			Element apiRevision = apiProperties.element("revision");
			String tempAPiRevision = apiRevision.getText();

			if (!Objects.equals(tempReversion, DEFAULT_REVISION ) || !Objects.equals(tempVersionApiDelayed, DEFAULT_API_DELAYED_VERSION) || !Objects.equals(tempAPiRevision, DEFAULT_API_DELAYED_VERSION)) {
				revision.setText(DEFAULT_REVISION);
				versionApiDelayed.setText(DEFAULT_API_DELAYED_VERSION);
				POMUtils.writeDocument(parentDocument, new File(absolutePathByArtifactId.get(PARENT_PROJECT_NAME)));

				apiRevision.setText(DEFAULT_REVISION);
				POMUtils.writeDocument(apiDelayedDocument, new File(absolutePathByArtifactId.get(API_DELAYED_PROJECT_NAME)));
			}

			System.out.println("\u001B[31m>>>>>>> 已还原版本，开始push代码 \u001B[0m");
			ArrayList<String> pomFiles = Lists.newArrayList(absolutePathByArtifactId.get(PARENT_PROJECT_NAME),
					absolutePathByArtifactId.get(API_DELAYED_PROJECT_NAME));
			GitUtils.commitAndPushCode(System.getenv(TARGET_PROJECT_FOLDER), pomFiles, "revert version");
			System.exit(1);
        }

        if (projects.size() == 1 && DEV_FLAG.equalsIgnoreCase(projects.get(0))) {
			boolean firstUpdate = false;
			String currentBranch = GitUtils.getCurrentBranch(System.getenv(TARGET_PROJECT_FOLDER));

			Document parentDocument = POMUtils.getParentDocument();
			Element properties = parentDocument.getRootElement().element("properties");
			List<Element> elements = properties.elements();
			Element revision = null;
			Element versionApiDelayed = null;

			for (Element element : elements) {
				if (Objects.equals(element.getQName().getName(), "revision")) {
					revision = element;
				}
				if (Objects.equals(element.getQName().getName(), "version.api-delayed")) {
					versionApiDelayed = element;
				}
			}
			if (Objects.isNull(revision) || Objects.isNull(versionApiDelayed)) {
				System.out.println("\u001B[31m>>>>>>> 请检查parent pom.xml文件中是否有revision和version.api-delayed节点 \u001B[0m");
				System.exit(1);
			}

			String tempReversion = revision.getText();
			String tempVersionApiDelayed = versionApiDelayed.getText();

			Document apiDelayedDocument = POMUtils.getApiDelayedDocument();
			Element apiProperties = apiDelayedDocument.getRootElement().element("properties");
			List<Element> apiElements = apiProperties.elements();
			Element apiRevision = null;
			for (Element element : apiElements) {
				if (Objects.equals(element.getQName().getName(), "revision")) {
					apiRevision = element;
				}
			}

			if (Objects.isNull(apiRevision)) {
				System.out.println("\u001B[31m>>>>>>> 请检查api-delayed pom.xml文件中是否有revision节点 \u001B[0m");
				System.exit(1);
			}

			String tempAPiRevision = apiRevision.getText();

          Map<String, String> absolutePathByArtifactId = DeployUtils.findAllMavenProjects();
          if (!Objects.equals(tempReversion, currentBranch + SNAPSHOT_SUFFIX ) || !Objects.equals(tempVersionApiDelayed, currentBranch + SNAPSHOT_SUFFIX) || !Objects.equals(tempAPiRevision, currentBranch + SNAPSHOT_SUFFIX)) {
        revision.setText(currentBranch + SNAPSHOT_SUFFIX);
        versionApiDelayed.setText(currentBranch + SNAPSHOT_SUFFIX);
        POMUtils.writeDocument(parentDocument, new File(absolutePathByArtifactId.get(PARENT_PROJECT_NAME)));

        apiRevision.setText(currentBranch + SNAPSHOT_SUFFIX);
				POMUtils.writeDocument(apiDelayedDocument, new File(absolutePathByArtifactId.get(API_DELAYED_PROJECT_NAME)));
				firstUpdate = true;
			}

			List<String> pomFiles = Lists.newArrayList(absolutePathByArtifactId.get(API_DELAYED_PROJECT_NAME), absolutePathByArtifactId.get(PARENT_PROJECT_NAME));
			System.out.println("\u001B[32m>>>>>>> start to install " + pomFiles.size()
					+ " projects below : >>>>>>>\u001B[0m");
			pomFiles.forEach(System.out::println);
			DeployUtils.installToLocal(pomFiles);
			System.out.println("\u001B[32m>>>>>>> all projects install successfully >>>>>>>\u001B[0m");

			if (firstUpdate) {
				System.out.println("\u001B[31m>>>>>>> 已修改版本，是否需要push代码？\u001B[0m");
				Scanner scanner = new Scanner(System.in);
				System.out.print("\u001B[31m输入 'y' push代码，或者输入其他字符结束执行：\u001B[0m");
				String input = scanner.next();
				if (!input.equalsIgnoreCase("y")) {
					System.exit(1);
				}
//				GitUtils.commitAndPushCode(System.getenv(TARGET_PROJECT_FOLDER), pomFiles, String.format(DEFAULT_COMMIT_MSG, currentBranch + SNAPSHOT_SUFFIX));
				System.exit(1);
			}
        }

        if (projects.size() > 0 && CHAT_FLAG.equalsIgnoreCase(projects.get(0))) {

            projects.remove(0);
            String prompt = String.join(" ", projects);
            Message assistant = new Message("assistant",  "");
            String openaiApiKey = System.getenv(OPENAI_API_KEY);
            String openaiApiHost = System.getenv(OPENAI_API_HOST);
            if (StrUtil.isBlank(openaiApiHost)) {
                openaiApiHost = OPENAI_API_HOST_DEFAULT_VALUE;
            }
            if (StrUtil.isBlank(prompt)) {
                System.out.println("\u001B[31m>>>>>>> 输入的prompt为空，请重试 \u001B[0m");
                System.exit(1);
            }
            if (StrUtil.isBlank(openaiApiKey)) {
                System.out.println("\u001B[31m>>>>>>> 环境变量 " + OPENAI_API_KEY + " 为空，请设置后再继续 \u001B[0m");
                System.exit(1);
            }
            while (true) {
                if (accumulator.get() > 0L) {
                    Thread.sleep(200);
                    System.out.println("\n\n");
                    System.out.println("\u001B[32m>>>>>>> 请输入prompt继续聊天，按 control + c 退出聊天 \u001B[0m");
                    Scanner scanner = new Scanner(System.in);
                    try {
                        prompt = scanner.nextLine();
                    } catch (Exception e) {
                        // do nothing
                    }
                }
                CountDownLatch countDownLatch = new CountDownLatch(1);
                messages.add(Message.of(prompt));
                ConsoleStreamListener listener = new ConsoleStreamListener();
                listener.setOnComplate(onComplete -> countDownLatch.countDown());
                ChatGPTStream chatGPTStream = ChatGPTStream.builder()
                    .apiKey(openaiApiKey)
                    .timeout(900)
                    .apiHost(openaiApiHost)
                    .build()
                    .init();
                ChatCompletion chatCompletion = ChatCompletion.builder()
                    .model(ChatCompletion.Model.GPT_3_5_TURBO.getName())
                    .messages(messages.getList())
                    .maxTokens(3000)
                    .temperature(0.9)
                    .build();
                chatGPTStream.streamChatCompletion(chatCompletion, listener);
                try {
                    countDownLatch.await();
                } catch (InterruptedException e) {
                    e.printStackTrace();
					Thread.currentThread().interrupt();
                }
                String content =  String.join("",  listener.getMessages());
                assistant  = new Message("assistant", content);
                if (!StrUtil.isBlank(assistant.getContent())) {
                    messages.add(assistant);
                }
                listener.clearMessages();
                accumulator.accumulate(1L);
            }
        }

        // 模糊匹配项目
        if (projects.size() == 1 && ALL_DEPLOY_FLAG.equalsIgnoreCase(projects.get(0))) {
            projects = DeployUtils.getAllNeedDeployedProjects().stream()
                .map(project -> "[" + project + "]").collect(Collectors.toList());
        }


        if (!CollUtil.isEmpty(projects) && !FIX_FLAG.equalsIgnoreCase(projects.get(0))) {
            List<String> candidatePomFiles = matchProject(projects);
            deployFile(candidatePomFiles);
            System.exit(1);
        }

        if (!CollUtil.isEmpty(projects) && FIX_FLAG.equalsIgnoreCase(projects.get(0))) {
            projects = projects.subList(1, projects.size());
            Map<String, String> absolutePathByArtifactId = DeployUtils.findAllMavenProjects();
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
            candidateProjects.forEach(candidate -> {
                ProcessBuilder processBuilder = new ProcessBuilder("mvn", "idea:idea");
                String path = absolutePathByArtifactId.get(candidate);
                int index = path.lastIndexOf("/pom.xml");
                processBuilder.directory(new File(path.substring(0, index)));
                int exitCode;
                try {
                    // 启动进程并等待完成
                    Process process = processBuilder.start();
                    exitCode = process.waitFor();
                    if (exitCode == 0) {
                        new BufferedReader(new InputStreamReader(process.getInputStream()));
                        BufferedReader reader = new BufferedReader(
                            new InputStreamReader(process.getInputStream()));
                        String line;
                        while ((line = reader.readLine()) != null) {
                            System.out.println("\u001B[37m" + line + " \u001B[0m");
                        }
                        System.out.println("\u001B[32m>>>>>>> " + candidate
                        + " dependency fix successfully ! >>>>>>> \u001B[0m");
                    } else {
                        System.err.println(
                            "\u001B[31m mvn command failed with exit code " + exitCode + "!\u001B[0m");
                        BufferedReader errorReader = new BufferedReader(new InputStreamReader(process.getErrorStream()));
                        String line;
                        while ((line = errorReader.readLine()) != null) {
                            System.err.println("\u001B[31m" +  line + " \u001B[0m");
                        }
                        System.out.println("\u001B[31m>>>>>>> " + candidate
                        + " dependency fix failure ! >>>>>>> \u001B[0m");
                    }
                } catch (IOException | InterruptedException e) {
                    System.out.println("\u001B[31m>>>>>>> " + candidate
                        + " dependency fix failure ! >>>>>>> \u001B[0m");
					Thread.currentThread().interrupt();
                }
            });
            System.exit(1);
        }
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
        File rootDir = new File(System.getenv(TARGET_PROJECT_FOLDER));
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
		List<SortHelper> sortHelpers = new ArrayList<>();
		candidatePomFiles.forEach(c -> {
			SortHelper sortHelper = new SortHelper();
			sortHelper.setName(c);
			String artifactId = artifactIdByAbsolutePath.get(c);
			if (StrUtil.isNotBlank(artifactId)) {
				sortHelper.setSort(DeployUtils.indexOf(artifactId));
			}
			sortHelpers.add(sortHelper);

		});
		List<String> sortedCandidatePomFiles = sortHelpers.stream().sorted(Comparator.comparing(SortHelper::getSort))
				.map(SortHelper::getName).collect(Collectors.toList());

		System.out.println("\u001B[32m>>>>>>> start to deploy " + sortedCandidatePomFiles.size()
            + " projects below sequencelly: >>>>>>>\u001B[0m");
		sortedCandidatePomFiles.forEach(System.out::println);
        Set<String> deployFailureProjects = new HashSet<>();
		sortedCandidatePomFiles.forEach(candidate -> {
            ProcessBuilder processBuilder = new ProcessBuilder("mvn", "deploy");
            int index = candidate.lastIndexOf("/pom.xml");
            processBuilder.directory(new File(candidate.substring(0, index)));
            int exitCode;
            try {
                // 启动进程并等待完成
                Process process = processBuilder.start();
                exitCode = process.waitFor();
                if (exitCode == 0) {
                    new BufferedReader(new InputStreamReader(process.getInputStream()));
                    BufferedReader reader = new BufferedReader(
                        new InputStreamReader(process.getInputStream()));
                    String line;
                    while ((line = reader.readLine()) != null) {
                        System.out.println("\u001B[37m" + line + " \u001B[0m");
                    }
                } else {
                    System.err.println(
                        "\u001B[31m mvn command failed with exit code " + exitCode + "!\u001B[0m");
                    BufferedReader errorReader = new BufferedReader(new InputStreamReader(process.getErrorStream()));
                    String line;
                    while ((line = errorReader.readLine()) != null) {
                        System.err.println("\u001B[31m" +  line + " \u001B[0m");
                    }
                    deployFailureProjects.add(candidate);
                }
            } catch (IOException | InterruptedException e) {
                System.out.println(
                    "\u001B[31m>>>>>>> " + candidate + " deploy failure !\u001B[0m");
                deployFailureProjects.add(candidate);
				Thread.currentThread().interrupt();
            }
        });
        System.out.println("\u001B[32m>>>>>>> deploy情况如下: >>>>>>>\u001B[0m");
        if (deployFailureProjects.isEmpty()) {
            System.out.println(
                "\u001B[32m>>>>>>> all projects deploy successfully >>>>>>>\u001B[0m");
        } else {
			deployFail.set(true);
            deployFailureProjects.forEach(deploy -> System.out.println(
                "\u001B[31m>>>>>>> " + deploy + " deploy failure !\u001B[0m"));
        }
    }
    private static String generateFinalNewVersion(List<String> splits) {
        String finalNewVersion;
        int patchVersion = Integer.parseInt(splits.get(splits.size() - 2));
        int newPatchVersion = patchVersion + INCREMENT;
        if (newPatchVersion > PATCH_VERSION_THRESHOLD) {
            int minorVersion = Integer.parseInt(splits.get(splits.size() - 3));
            int newMinorVersion = minorVersion + INCREMENT;
            if (newMinorVersion > MINOR_VERSION_THRESHOLD) {
                int majorVersion = Integer.parseInt(splits.get(splits.size() - 4));
                int newMajorVersion = majorVersion + INCREMENT;
                if (newMajorVersion > MAJOR_VERSION_THRESHOLD) {
                    throw new EasyDeployException("主版本号超过设定的阈值");
                } else {
                    splits.set(splits.size() - 4, String.valueOf(newMajorVersion));
                }
            } else {
                splits.set(splits.size() - 3, String.valueOf(newMinorVersion));
            }
        } else {
            splits.set(splits.size() - 2, String.valueOf(newPatchVersion));
        }
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

    private static void fillMaps() {
		String targetProjectFolder = System.getenv(TARGET_PROJECT_FOLDER);
		if (StrUtil.isBlank(targetProjectFolder)) {
			System.err.println(
					"\u001B[31m please set TARGET_PROJECT_FOLDER env viriable " + "!\u001B[0m");
			System.exit(1);
		}
		File rootDir = new File(targetProjectFolder);
        Iterator<File> iterator = Files.fileTraverser().depthFirstPreOrder(rootDir).iterator();
        while (iterator.hasNext()) {
            File file = iterator.next();
            if (file.isFile() && file.getName().equals("pom.xml")) {
                String parentName = file.getParentFile().getName();
                if (DeployUtils.contains(parentName)) {
                    absolutePathByArtifactId.put(parentName, file.getAbsolutePath());
                    artifactIdByAbsolutePath.put(file.getAbsolutePath(), parentName);
                }
            }
        }
    }
}
