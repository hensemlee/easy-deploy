package com.hensemlee;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.google.common.collect.Lists;
import com.hensemlee.command.*;
import com.hensemlee.executor.*;
import org.apache.commons.lang3.StringUtils;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static com.hensemlee.contants.Constants.TARGET_PROJECT_FOLDER;

/**
 * @author hensemlee
 * @create 2023/3/4 13:33
 */
public class EasyMavenDeployTool {
	private static List<ICommand> commands = Lists.newArrayList();

    static {
		initialCheck();
		commands.add(new EasyDeployChatCommand(new EasyDeployChatExecutor()));
		commands.add(new EasyDeployDevCommand(new EasyDeployDevExecutor()));
		commands.add(new EasyDeployFixCommand(new EasyDeployFixExecutor()));
		commands.add(new EasyDeployGithubTrendingCommand(new EasyDeployGithubTrendingExecutor()));
		commands.add(new EasyDeployPrdCommand(new EasyDeployPrdExecutor()));
		commands.add(new EasyDeployUpgradeCommand(new EasyDeployUpgradeExecutor()));
		commands.add(new EasyDeployTestCommand(new EasyDeployTestExecutor()));
		commands.add(new EasyDeployEnforceCommand(new EasyDeployEnforceExecutor()));
		commands.add(new EasyDeployEnforceInitDataCommand(new EasyDeployEnforceInitDataExecutor()));
    }

    public static void main(String[] args) {
        if (args.length == 0) {
			echoHelp();
		}
        List<String> arguments = Arrays.stream(args).filter(StringUtils::isNotBlank).collect(Collectors.toList());
        if (CollUtil.isEmpty(arguments)) {
			echoHelp();
        }
		for (ICommand command : commands) {
			command.execute(arguments);
		}
		echoHelp();
    }

	private static void echoHelp() {
		System.out.println(
				"\u001B[32mUsage:\u001B[0m");
		System.out.println(
			"\u001B[32measy-deploy fix project1 project2 ...\u001B[0m");
		System.out.println(
			"\u001B[32m(解决依赖引用不到的情况)\u001B[0m");
		System.out.println(
			"\u001B[32measy-deploy dev \u001B[0m");
		System.out.println(
			"\u001B[32m(开始开发时，需要改动公共包，该命令一键修改开发版本为当前分支名的SNAPSHOT版本并进行本地install)\u001B[0m");
		System.out.println(
				"\u001B[32measy-deploy test \u001B[0m");
		System.out.println(
			"\u001B[32m(开发后，想发到测试环境进行部署，该命令一键修改开发版本为当前分支名的SNAPSHOT版本并进行本地install并deploy至远程仓库)\u001B[0m");
		System.out.println(
			"\u001B[32measy-deploy prd \u001B[0m");
		System.out.println(
			"\u001B[32m(上线生产前，将开发版本号一键还原为默认版本号)\u001B[0m");
		System.out.println(
			"\u001B[32measy-deploy chat xxxxxxx \u001B[0m");
		System.out.println(
			"\u001B[32m(在IDE里开发时，随时可在命令行发起ChatGPT提问&聊天)\u001B[0m");
		System.out.println(
				"\u001B[32measy-deploy gt \u001B[0m");
		System.out.println(
				"\u001B[32m(命令行即时获取Github Trending日周月榜单)\u001B[0m");
		System.out.println(
				"\u001B[32measy-deploy upgrade \u001B[0m");
		System.out.println(
				"\u001B[32m(升级到最新easy-deploy)\u001B[0m");
		System.out.println(
				"\u001B[32measy-deploy enforce project1 project2 ...\u001B[0m");
		System.out.println(
				"\u001B[32m增加依赖后，检测依赖冲突的个数是否少于master分支某个时刻记录的个数，不多于则检测通过\u001B[0m");
		System.exit(1);
	}

    private static void initialCheck() {
		String targetProjectFolder = System.getenv(TARGET_PROJECT_FOLDER);
		if (StrUtil.isBlank(targetProjectFolder)) {
			System.err.println("\u001B[31m please set TARGET_PROJECT_FOLDER env viriable " + "!\u001B[0m");
			System.exit(1);
		}
	}
}
