package com.hensemlee.command;

import com.hensemlee.executor.IExecutor;

import java.util.List;

import static com.hensemlee.contants.Constants.TEST_FLAG;

/**
 * @author hensemlee
 * @email lijun@tezign.com
 * @create 2023/6/4 11:33
 */
public class EasyDeployTestCommand implements ICommand {
	private IExecutor executor;

	public EasyDeployTestCommand(IExecutor executor) {
		this.executor = executor;
	}

	@Override
	public boolean canExecute(List<String> args) {
		return args.size() == 1 && TEST_FLAG.equalsIgnoreCase(args.get(0));
	}

	@Override
	public void execute(List<String> args) {
		if (!canExecute(args)) {
			return;
		}
		executor.doExecute(args);
	}
}
