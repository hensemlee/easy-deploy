package com.hensemlee;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.google.common.collect.Lists;
import com.google.common.io.Files;
import com.hensemlee.bean.FixedSizeQueue;
import com.hensemlee.bean.SortHelper;
import com.hensemlee.bean.req.EffectiveMavenReq;
import com.hensemlee.exception.EasyDeployException;
import com.hensemlee.util.*;
import com.plexpt.chatgpt.ChatGPTStream;
import com.plexpt.chatgpt.entity.chat.ChatCompletion;
import com.plexpt.chatgpt.entity.chat.Message;
import com.plexpt.chatgpt.listener.ConsoleStreamListener;
import org.apache.commons.lang3.StringUtils;
import org.dom4j.Document;
import org.dom4j.Element;

import java.io.*;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.LongAccumulator;
import java.util.stream.Collectors;

import static com.hensemlee.contants.Constants.*;

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
        initialCheck();
    }

    public static void main(String[] args) throws IOException, InterruptedException {
        if (args.length == 0) {
			echoHelp();
		}
        List<String> projects = Arrays.stream(args).filter(StringUtils::isNotBlank).collect(Collectors.toList());
        if (CollUtil.isEmpty(projects)) {
			echoHelp();
        }

		if (projects.size() == 1 && TEST_FLAG.equalsIgnoreCase(projects.get(0))) {
			boolean firstUpdate = false;
			String currentExecutionPath = System.getProperty(CURRENT_PATH);
			String targetProjectFolder = PathUtils.tryGetCurrentExecutionPath(currentExecutionPath);
			String currentBranch = GitUtils.getCurrentBranch(targetProjectFolder);
			currentBranch = removeIllegalChars(currentBranch);
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
			Map<String, String> absolutePathByArtifactId = DeployUtils.findAllMavenProjects(targetProjectFolder);
			if (!Objects.equals(tempReversion, currentBranch + SNAPSHOT_SUFFIX ) || !Objects.equals(tempVersionApiDelayed, currentBranch + SNAPSHOT_SUFFIX) || !Objects.equals(tempAPiRevision, currentBranch + SNAPSHOT_SUFFIX)) {
				revision.setText(currentBranch + SNAPSHOT_SUFFIX);
				versionApiDelayed.setText(currentBranch + SNAPSHOT_SUFFIX);
				POMUtils.writeDocument(parentDocument, new File(absolutePathByArtifactId.get(PARENT_PROJECT_NAME)));

				apiRevision.setText(currentBranch + SNAPSHOT_SUFFIX);
				POMUtils.writeDocument(apiDelayedDocument, new File(absolutePathByArtifactId.get(API_DELAYED_PROJECT_NAME)));
				firstUpdate = true;
			}

			List<String> pomFiles = Lists.newArrayList(absolutePathByArtifactId.get(API_DELAYED_PROJECT_NAME), absolutePathByArtifactId.get(PARENT_PROJECT_NAME));
			System.out.println("\u001B[32m>>>>>>> start to deploy " + pomFiles.size()
					+ " projects below sequencelly: >>>>>>>\u001B[0m");
			pomFiles.forEach(System.out::println);
			Set<String> installFailureProjects = new HashSet<>();
			System.out.println("\u001B[31m>>>>>>> 即将执行mvn deploy, 默认会先执行mvn clean, 是否需要跳过mvn clean？(mvn clean命令可能会导致整个edp test命令执行耗时更长，但可防止一定的问题出现)\u001B[0m");
			System.out.print("\u001B[31m输入 'y' 跳过执行mvn clean，或者输入其他字符继续执行mvn clean：\u001B[0m");
			Scanner scanner = new Scanner(System.in);
			String in = scanner.next();
			boolean cleanSkip = false;
			if (in.equalsIgnoreCase("y")) {
				cleanSkip = true;
			}
			for (String pom : pomFiles) {
				AtomicBoolean success = new AtomicBoolean(true);
				deploy(pom.substring(0, pom.lastIndexOf("/pom.xml")), success, cleanSkip);
				if (!success.get()) {
					installFailureProjects.add(pom);
				}
			}
			System.out.println("\u001B[32m>>>>>>> deploy情况如下: >>>>>>>\u001B[0m");
			if (installFailureProjects.isEmpty()) {
				System.out.println(
						"\u001B[32m>>>>>>> all projects deploy successfully >>>>>>>\u001B[0m");
			} else {
				installFailureProjects.forEach(deploy -> System.out.println(
						"\u001B[31m>>>>>>> " + deploy + " deploy failure !\u001B[0m"));
				System.exit(1);
			}

			if (firstUpdate) {
				System.out.println("\u001B[31m>>>>>>> 已修改版本，是否需要push？\u001B[0m");
				System.out.print("\u001B[31m输入 'y' push代码，或者输入其他字符结束执行：\u001B[0m");
				String input = scanner.next();
				if (!input.equalsIgnoreCase("y")) {
					System.exit(1);
				}
				GitUtils.commitAndPushCode(System.getenv(targetProjectFolder), pomFiles, String.format(DEFAULT_COMMIT_MSG, currentBranch + SNAPSHOT_SUFFIX));
			}
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
			Map<String, String> absolutePathByArtifactId = DeployUtils.findAllMavenProjects(PathUtils.tryGetCurrentExecutionPath(System.getProperty(CURRENT_PATH)));
			if (!Objects.equals(tempReversion, DEFAULT_LOCAL_VERSION) || !Objects.equals(tempVersionApiDelayed,
					DEFAULT_LOCAL_VERSION) || !Objects.equals(tempAPiRevision, DEFAULT_LOCAL_VERSION)) {
				revision.setText(DEFAULT_LOCAL_VERSION);
				versionApiDelayed.setText(DEFAULT_LOCAL_VERSION);
				POMUtils.writeDocument(parentDocument, new File(absolutePathByArtifactId.get(PARENT_PROJECT_NAME)));

				apiRevision.setText(DEFAULT_LOCAL_VERSION);
				POMUtils.writeDocument(apiDelayedDocument, new File(absolutePathByArtifactId.get(API_DELAYED_PROJECT_NAME)));
			}

			System.out.println("\u001B[31m>>>>>>> 已还原版本，是否需要push？ \u001B[0m");
			Scanner scanner = new Scanner(System.in);
			System.out.print("\u001B[31m输入 'y' push代码，或者输入其他字符结束执行：\u001B[0m");
			String input = scanner.next();
			if (!input.equalsIgnoreCase("y")) {
				System.exit(1);
			}
			ArrayList<String> pomFiles = Lists.newArrayList(absolutePathByArtifactId.get(API_DELAYED_PROJECT_NAME), absolutePathByArtifactId.get(PARENT_PROJECT_NAME));
			GitUtils.commitAndPushCode(PathUtils.tryGetCurrentExecutionPath(System.getProperty(CURRENT_PATH)), pomFiles, "\"revert version\"");
			System.exit(1);
        }

        if (projects.size() == 1 && DEV_FLAG.equalsIgnoreCase(projects.get(0))) {
			boolean firstUpdate = false;
			String targetProjectFolder = PathUtils.tryGetCurrentExecutionPath(System.getProperty(CURRENT_PATH));
			String currentBranch = GitUtils.getCurrentBranch(targetProjectFolder);
			currentBranch = removeIllegalChars(currentBranch);
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
			Map<String, String> absolutePathByArtifactId = DeployUtils.findAllMavenProjects(PathUtils.tryGetCurrentExecutionPath(System.getProperty(CURRENT_PATH)));
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
					+ " projects below sequencelly: >>>>>>>\u001B[0m");
			pomFiles.forEach(System.out::println);
			Set<String> installFailureProjects = new HashSet<>();
			System.out.println("\u001B[31m>>>>>>> 即将执行mvn install, 默认会先执行mvn clean, 是否需要跳过mvn clean？(mvn clean命令可能会导致整个edp dev命令执行耗时更长，但可防止一定的问题出现)\u001B[0m");
			System.out.print("\u001B[31m输入 'y' 跳过执行mvn clean，或者输入其他字符继续执行mvn clean：\u001B[0m");
			Scanner scanner = new Scanner(System.in);
			String in = scanner.next();
			boolean cleanSkip = false;
			if (in.equalsIgnoreCase("y")) {
				cleanSkip = true;
			}
			for (String pom : pomFiles) {
				AtomicBoolean success = new AtomicBoolean(true);
				install(pom.substring(0, pom.lastIndexOf("/pom.xml")), success, cleanSkip);
				if (!success.get()) {
					installFailureProjects.add(pom);
				}
			}
			System.out.println("\u001B[32m>>>>>>> install情况如下: >>>>>>>\u001B[0m");
			if (installFailureProjects.isEmpty()) {
				System.out.println(
						"\u001B[32m>>>>>>> all projects install successfully >>>>>>>\u001B[0m");
			} else {
				installFailureProjects.forEach(deploy -> System.out.println(
						"\u001B[31m>>>>>>> " + deploy + " install failure !\u001B[0m"));
				System.exit(1);
			}

			if (firstUpdate) {
				System.out.println("\u001B[31m>>>>>>> 已修改版本，是否需要push？\u001B[0m");
				System.out.print("\u001B[31m输入 'y' push代码，或者输入其他字符结束执行：\u001B[0m");
				String input = scanner.next();
				if (!input.equalsIgnoreCase("y")) {
					System.exit(1);
				}
				GitUtils.commitAndPushCode(targetProjectFolder, pomFiles, String.format(DEFAULT_COMMIT_MSG, currentBranch + SNAPSHOT_SUFFIX));
			}
			System.exit(1);
        }

		if (projects.size() == 1 && GIT_TRENDING_FLAG.equalsIgnoreCase(projects.get(0))) {
			GitHubTrendingUtils.top25();
			System.exit(1);
		}

		if (projects.size() == 1 && UPGRADE_FLAG.equalsIgnoreCase(projects.get(0))) {
			UpdateUtils.update();
			System.exit(1);
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

        if (!CollUtil.isEmpty(projects) && FIX_FLAG.equalsIgnoreCase(projects.get(0))) {
            projects = projects.subList(1, projects.size());
            Map<String, String> absolutePathByArtifactId = DeployUtils.findAllMavenProjects(PathUtils.tryGetCurrentExecutionPath(System.getProperty(CURRENT_PATH)));
            Set<String> candidateProjects = new HashSet<>();
            List<String> allNeedDeployedProjects = DeployUtils.getAllNeedDeployedProjects();
            Map<String, Set<String>> prompt = new HashMap<>();
            projects.forEach(project -> {
                Set<String> promptSet = new HashSet<>();
                absolutePathByArtifactId.forEach((k, v) -> {
                    if (project.startsWith("{") && project.endsWith("}")) {
                        String format = project.substring(1, project.length() - 1);
                        if (k.equals(format)) {
                            candidateProjects.add(k);
                            promptSet.add(k);
                        }
                    } else if ((k.contains(project) || k.equals(project)) && !allNeedDeployedProjects.contains(k)) {
                        candidateProjects.add(k);
                        promptSet.add(k);
                    }
                });
                prompt.put(project, promptSet);
            });
            promotion(prompt);
            candidateProjects.forEach(candidate -> {
				try {
					ProcessBuilder processBuilder = new ProcessBuilder("mvn", "-T", "1C", "clean", "install", "-DskipTests", "-Dmaven.javadoc.skip=true");
					String path = absolutePathByArtifactId.get(candidate);
					int index = path.lastIndexOf("/pom.xml");
					processBuilder.directory(new File(path.substring(0, index)));
					processBuilder.redirectErrorStream(true);
					Process process = processBuilder.start();
					InputStream is = process.getInputStream();
					BufferedReader reader = new BufferedReader(new InputStreamReader(is));
					String line;
					while ((line = reader.readLine()) != null) {
						System.out.println(line);
					}
					int exitCode = process.waitFor();
					System.out.println(">>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>: " + exitCode);
				} catch (IOException | InterruptedException e) {
					e.printStackTrace();
				}
            });
            System.exit(1);
        }
		echoHelp();
    }

	private static void echoHelp() {
		System.out.println(
			"\u001B[32mUsage: easy-deploy fix project1 project2 ...\u001B[0m");
		System.out.println(
			"\u001B[32m       (解决依赖引用不到的情况)\u001B[0m");
		System.out.println(
			"\u001B[32mUsage: easy-deploy dev \u001B[0m");
		System.out.println(
			"\u001B[32m       (开始开发时，需要改动公共包，该命令一键修改开发版本为当前分支名的SNAPSHOT版本并进行本地install)\u001B[0m");
		System.out.println(
				"\u001B[32mUsage: easy-deploy test \u001B[0m");
		System.out.println(
			"\u001B[32m       (开发后，想发到测试环境进行部署，该命令一键修改开发版本为当前分支名的SNAPSHOT版本并进行本地install并deploy至远程仓库)\u001B[0m");
		System.out.println(
			"\u001B[32mUsage: easy-deploy prd \u001B[0m");
		System.out.println(
			"\u001B[32m       (上线生产前，将开发版本号一键还原为默认版本号)\u001B[0m");
		System.out.println(
			"\u001B[32mUsage: easy-deploy chat xxxxxxx \u001B[0m");
		System.out.println(
			"\u001B[32m       (在IDE里开发时，随时可在命令行发起ChatGPT提问&聊天)\u001B[0m");
		System.out.println(
				"\u001B[32mUsage: easy-deploy gt \u001B[0m");
		System.out.println(
				"\u001B[32m   (命令行即时获取Github Trending日周月榜单)\u001B[0m");
		System.out.println(
				"\u001B[32mUsage: easy-deploy upgrade \u001B[0m");
		System.out.println(
				"\u001B[32m   (升级到最新easy-deploy)\u001B[0m");
		System.exit(1);
	}

	private static void install(String path, AtomicBoolean success, boolean cleanSkip) throws IOException, InterruptedException {
		ProcessBuilder pb = new ProcessBuilder("mvn", "-T", "1C", "clean", "install", "-DskipTests", "-Dmaven.javadoc.skip=true");
		if (cleanSkip) {
			pb = new ProcessBuilder("mvn", "-T", "1C", "install", "-DskipTests", "-Dmaven.javadoc.skip=true");
		}
		pb.directory(new File(path));
		pb.redirectErrorStream(true);
		Process process = pb.start();
		InputStream is = process.getInputStream();
		BufferedReader reader = new BufferedReader(new InputStreamReader(is));
		String line;
		while ((line = reader.readLine()) != null) {
			System.out.println(line);
		}
		int exitCode = process.waitFor();
		System.out.println(">>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>: " + exitCode);
		if (exitCode != 0) {
			success.set(false);
		}
	}

	private static void deploy(String path, AtomicBoolean success, boolean cleanSkip) throws IOException, InterruptedException {
		ProcessBuilder pb = new ProcessBuilder("mvn", "-T", "1C", "clean", "deploy", "-DskipTests", "-Dmaven.javadoc.skip=true");
		if (cleanSkip) {
			pb = new ProcessBuilder("mvn", "-T", "1C", "deploy", "-DskipTests", "-Dmaven.javadoc.skip=true");
		}
		pb.directory(new File(path));
		pb.redirectErrorStream(true);
		Process process = pb.start();
		InputStream is = process.getInputStream();
		BufferedReader reader = new BufferedReader(new InputStreamReader(is));
		String line;
		while ((line = reader.readLine()) != null) {
			System.out.println(line);
		}
		int exitCode = process.waitFor();
		System.out.println(">>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>: " + exitCode);
		if (exitCode != 0) {
			success.set(false);
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

	@Deprecated
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

		System.out.println("\u001B[32m>>>>>>> start to deploy " + sortedCandidatePomFiles.size() + " projects below sequencelly: >>>>>>>\u001B[0m");
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

    private static void initialCheck() {
		String targetProjectFolder = System.getenv(TARGET_PROJECT_FOLDER);
		if (StrUtil.isBlank(targetProjectFolder)) {
			System.err.println("\u001B[31m please set TARGET_PROJECT_FOLDER env viriable " + "!\u001B[0m");
			System.exit(1);
		}
//		fillInitialData(targetProjectFolder);
	}

//	private static void fillInitialData(String targetProjectFolder) {
//		File rootDir = new File(targetProjectFolder);
//		Iterator<File> iterator = Files.fileTraverser().depthFirstPreOrder(rootDir).iterator();
//		while (iterator.hasNext()) {
//			File file = iterator.next();
//			if (file.isFile() && file.getName().equals("pom.xml")) {
//				String parentName = file.getParentFile().getName();
//				if (DeployUtils.contains(parentName)) {
//					absolutePathByArtifactId.put(parentName, file.getAbsolutePath());
//					artifactIdByAbsolutePath.put(file.getAbsolutePath(), parentName);
//				}
//			}
//		}
//	}

	private static String removeIllegalChars(String str) {
		// 替换 / : " < > | ? *  为 -
		return str.replaceAll("[/:\"><|?*]", "-");
	}
}
