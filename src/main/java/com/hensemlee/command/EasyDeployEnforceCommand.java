package com.hensemlee.command;

import com.hensemlee.executor.IExecutor;

import java.util.List;

import static com.hensemlee.contants.Constants.ENFORCE_FLAG;

/**
 * @author hensemlee
 * @owner lijun
 * @team Research and Development Efficiency.
 * @since 2023/6/12 15:18
 */
public class EasyDeployEnforceCommand implements ICommand {

	private IExecutor executor;

	public EasyDeployEnforceCommand(IExecutor executor) {
		this.executor = executor;
	}

	@Override
	public boolean canExecute(List<String> args) {
		return args.size() > 0 && ENFORCE_FLAG.equalsIgnoreCase(args.get(0));
	}

	@Override
	public void execute(List<String> args) {
		if (!canExecute(args)) {
			return;
		}
		executor.doExecute(args);
	}
}
