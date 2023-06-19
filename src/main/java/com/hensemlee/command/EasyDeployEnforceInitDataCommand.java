package com.hensemlee.command;

import com.hensemlee.executor.IExecutor;

import java.util.List;

import static com.hensemlee.contants.Constants.ENFORCE_FLAG;
import static com.hensemlee.contants.Constants.ENFORCE_INIT_DATA_FLAG;

/**
 * @author hensemlee
 * @owner lijun
 * @team Research and Development Efficiency.
 * @since 2023/6/12 2023-06-14 16:15
 */
public class EasyDeployEnforceInitDataCommand implements ICommand {

	private IExecutor executor;

	public EasyDeployEnforceInitDataCommand(IExecutor executor) {
		this.executor = executor;
	}

	@Override
	public boolean canExecute(List<String> args) {
		return args.size() > 0 && ENFORCE_INIT_DATA_FLAG.equalsIgnoreCase(args.get(0));
	}

	@Override
	public void execute(List<String> args) {
		if (!canExecute(args)) {
			return;
		}
		executor.doExecute(args);
	}
}
