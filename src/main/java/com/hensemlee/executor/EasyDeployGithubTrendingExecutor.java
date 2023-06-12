package com.hensemlee.executor;

import com.hensemlee.exception.EasyDeployException;
import com.hensemlee.util.GitHubTrendingUtils;

import java.io.IOException;
import java.util.List;

/**
 * @author hensemlee
 * @email lijun@tezign.com
 * @create 2023/6/4 11:31
 */
public class EasyDeployGithubTrendingExecutor implements IExecutor {

	@Override
	public void doExecute(List<String> args) {
		try {
			GitHubTrendingUtils.top25();
		} catch (IOException e) {
			throw new EasyDeployException(e.getMessage());
		}
		System.exit(1);
	}
}
