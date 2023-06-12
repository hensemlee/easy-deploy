package com.hensemlee.executor;

import com.hensemlee.exception.EasyDeployException;
import com.hensemlee.util.UpdateUtils;

import java.util.List;

/**
 * @author hensemlee
 * @email lijun@tezign.com
 * @create 2023/6/4 11:31
 */
public class EasyDeployUpgradeExecutor implements IExecutor {
	@Override
	public void doExecute(List<String> args) {
		try {
			UpdateUtils.update();
		} catch (Exception e) {
			throw new EasyDeployException(e.getMessage());
		}
		System.exit(1);
	}
}
