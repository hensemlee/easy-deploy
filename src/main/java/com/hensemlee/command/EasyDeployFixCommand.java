package com.hensemlee.command;


import cn.hutool.core.collection.CollUtil;
import com.hensemlee.executor.IExecutor;

import java.util.List;

import static com.hensemlee.contants.Constants.FIX_FLAG;

/**
 * @author hensemlee
 * @email lijun@tezign.com
 * @create 2023/6/4 11:31
 */
public class EasyDeployFixCommand implements ICommand {

	private IExecutor executor;

	public EasyDeployFixCommand(IExecutor executor) {
		this.executor = executor;
	}

	@Override
	public boolean canExecute(List<String> args) {
		return !CollUtil.isEmpty(args) && FIX_FLAG.equalsIgnoreCase(args.get(0));
	}

	@Override
	public void execute(List<String> args) {
		if (!canExecute(args)) {
			return;
		}
		executor.doExecute(args);
	}
}
