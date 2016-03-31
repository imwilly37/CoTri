package extractionCDRapi;

import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.Reader;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Runs the specified command as a (separate) native process in the specified directory. Use the await() method to wait for the results, then getResult() to retrieve it.
 * <u style="color:gray"> Reference: <a href="http://www.ncbi.nlm.nih.gov/CBBresearch/Lu/Demo/DNorm/">DNorm</a></u><br>
 * @author leamanjr
 */
public class ProcessRunner {

	private String command;
	private String dir;
	private CountDownLatch latch;
	private String result;
	private String error;

	public ProcessRunner(String command, String dir) {
		this.command = command;
		this.dir = dir;
		this.latch = new CountDownLatch(1);
		Runnable runner = new Runnable() {
			public void run() {
				runProcess();
			}
		};
		new Thread(runner).start();
	}
	
	public ProcessRunner(String command, String dir,final String inputStr) {
		this.command = command;
		this.dir = dir;
		this.latch = new CountDownLatch(1);
		Runnable runner = new Runnable() {
			public void run() {
				runProcess(inputStr);
			}
		};
		new Thread(runner).start();
	}

	/**
	 * Returns the result of the process, or null if there was an error.
	 * 
	 * @return the result of the process
	 */
	public String getResult() {
		return result;
	}

	/**
	 * Returns a String describing errors encountered while running the process, or null if there was no error.
	 * 
	 * @return a string describing errors
	 */
	public String getError() {
		return error;
	}

	/**
	 * Waits for the process to complete, up to the specified number of milliseconds.
	 * 
	 * @param milliseconds how much milliseconds you want to wait
	 */
	public void await(long milliseconds) {
		try {
			latch.await(milliseconds, TimeUnit.MILLISECONDS);
		} catch (InterruptedException e) {
			error = e.toString();
		}
	}

	private void runProcess() {
		Process p = null;
		try {
			p = Runtime.getRuntime().exec(command, null, new File(dir));
			
			final StringBuilder out = new StringBuilder();
			final char[] buffer = new char[512];
			final Reader in = new InputStreamReader(p.getInputStream(), "UTF-8");
			try {
				int length = in.read(buffer, 0, buffer.length);
				while (length >= 0) {
					out.append(buffer, 0, length);
					length = in.read(buffer, 0, buffer.length);
				}
			} finally {
				in.close();
			}
			result = out.toString();
			p.waitFor();
			p = null;
		} catch (IOException e) {
			result = null;
			error = e.toString();
		} catch (InterruptedException e) {
			result = null;
			error = e.toString();
		} finally {
			latch.countDown();
			if (p != null) {
				p.destroy();
			}
		}
	}
	private void runProcess(String inputStr) {
		Process p = null;
		try {
			p = Runtime.getRuntime().exec(command, null, new File(dir));
			OutputStream output = p.getOutputStream();
			output.write(inputStr.getBytes(), 0, inputStr.length());
			output.flush();
			output.close();
			p.waitFor();
			
			final StringBuilder out = new StringBuilder();
			final char[] buffer = new char[512];
			final Reader in = new InputStreamReader(p.getInputStream(), "UTF-8");
			try {
				int length = in.read(buffer, 0, buffer.length);
				while (length >= 0) {
					out.append(buffer, 0, length);
					length = in.read(buffer, 0, buffer.length);
				}
			} finally {
				in.close();
			}
			result = out.toString();
			p.waitFor();
			p = null;
		} catch (IOException e) {
			result = null;
			error = e.toString();
		} catch (InterruptedException e) {
			result = null;
			error = e.toString();
		} finally {
			latch.countDown();
			if (p != null) {
				p.destroy();
			}
		}
	}
}