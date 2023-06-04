package com.hensemlee.executor;

import cn.hutool.core.util.StrUtil;
import com.hensemlee.bean.FixedSizeQueue;
import com.plexpt.chatgpt.ChatGPTStream;
import com.plexpt.chatgpt.entity.chat.ChatCompletion;
import com.plexpt.chatgpt.entity.chat.Message;
import com.plexpt.chatgpt.listener.ConsoleStreamListener;

import java.util.List;
import java.util.Scanner;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.LongAccumulator;

import static com.hensemlee.contants.Constants.*;
import static com.hensemlee.contants.Constants.OPENAI_API_KEY;

/**
 * @author hensemlee
 * @email lijun@tezign.com
 * @create 2023/6/4 11:31
 */
public class EasyDeployChatExecutor implements IExecutor {

	private static LongAccumulator accumulator = new LongAccumulator(Long::sum, 0L);

	private static FixedSizeQueue<Message> messages = new FixedSizeQueue<>(10);

	@Override
	public void doExecute(List<String> args) {
		args.remove(0);
		String prompt = String.join(" ", args);
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
				try {
					Thread.sleep(200);
					System.out.println("\n\n");
					System.out.println("\u001B[32m>>>>>>> 请输入prompt继续聊天，按 control + c 退出聊天 \u001B[0m");
					Scanner scanner = new Scanner(System.in);
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
}
