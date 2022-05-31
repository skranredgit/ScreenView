package com.example.test5;

import java.io.IOException;
import java.io.InputStream;
import java.net.Socket;
import java.nio.ByteBuffer;

public class ConnectionWorker implements Runnable {
	private Socket clientSocket = null;
	private InputStream inputStream = null;
	public static String mess;
	public static ByteBuffer csd0;
	private static String CHANNEL_ID = "Cat channel";
	static boolean f;

	public ConnectionWorker(Socket socket) {
		clientSocket = socket;
	}

	@Override
	public void run() {
		try {
			inputStream = clientSocket.getInputStream();
		} catch (IOException e) {
			System.out.println("Cant get input stream");
		}
		byte[] buffer = new byte[1024 * 4];
		while (true) {
			try {
				int count = inputStream.read(buffer, 0, buffer.length);
				if (count > 0) {
					this.getId(new String(buffer, 0, count));
					csd0 = ByteBuffer.wrap(buffer);
					mess = new String(buffer, 0, count);
					f = true;
					System.out.println(mess);

				} else if (count == -1) {
					System.out.println("close socket");
					clientSocket.close();
					break;
				}
			} catch (IOException e) {
				System.out.println(e.getMessage());
			}
		}
	}

	public String getId(String k) {
		return k;
	}
}
