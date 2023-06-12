package com.hensemlee.command;

import java.util.List;

/**
 * @author hensemlee
 * @email lijun@tezign.com
 * @create 2023/6/4 11:29
 */
public interface ICommand {

	boolean canExecute(List<String> args);
	void execute(List<String> args);
}
