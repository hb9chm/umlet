package com.baselet.gui;

import java.awt.Desktop;
import java.io.IOException;
import java.lang.reflect.Method;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.net.URLDecoder;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;
import java.util.function.BiFunction;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.baselet.control.constants.SystemInfo;
import com.baselet.control.enums.Os;

public class BrowserLauncher {

	private static final Logger log = LoggerFactory.getLogger(BrowserLauncher.class);

	public static void openURL(String url) {

		try {
			// Since Java6 this is a much easier method to open the browser
			if (Desktop.isDesktopSupported()) {
				try {
					Desktop desktop = Desktop.getDesktop();
					desktop.browse(new URI(url));
					return;
				} catch (Exception e) {
					// #673: it is possible to have isDesktopSupported() return true but browser() still fails; then continue and try alternative browser selection
				}
			}

			// If desktop is not supported we try the old main specific code
			if (SystemInfo.OS == Os.MAC) {
				Class<?> fileMgr = Class.forName("com.apple.eio.FileManager");
				Method openURL = fileMgr.getDeclaredMethod("openURL", new Class[] { String.class });
				openURL.invoke(null, new Object[] { url });
			}
			else if (SystemInfo.OS == Os.WINDOWS) {
				Runtime.getRuntime().exec("rundll32 url.dll,FileProtocolHandler " + url);
			}
			else { // assume Unix or Linux
				try {
					Runtime.getRuntime().exec(new String[] { "xdg-open", url }); // #673 try xdg-open (see https://stackoverflow.com/questions/70348991/is-desktopbrowse-supported-on-linux-platform-just-for-gnome-desktop/70434441#70434441)
					return;
				} catch (IOException e) {
					// if it fails continue trying different browser names
				}
				String[] browsers = { "firefox", "chromium", "opera", "konqueror", "epiphany", "mozilla", "netscape" };
				String browser = null;
				for (int count = 0; count < browsers.length && browser == null; count++) {
					if (Runtime.getRuntime().exec(new String[] { "which", browsers[count] }).waitFor() == 0) {
						browser = browsers[count];
					}
				}
				if (browser == null) {
					throw new Exception("Could not find web browser");
				}
				else {
					Runtime.getRuntime().exec(new String[] { browser, url });
				}
			}
		} catch (Exception e) {
			log.error("Error at opening the URL.", e);
		}
	}

	public static String readURL(String url) throws IOException {
		Map<String, Integer> visited = new HashMap<String, Integer>();
		int times;
		HttpURLConnection con;
		while (true) { // follow redirects (see https://stackoverflow.com/q/1884230)
			times = visited.compute(url, new BiFunction<String, Integer, Integer>() {
				@Override
				public Integer apply(String key, Integer count) {
					return count == null ? 1 : count + 1;
				}
			});
			if (times > 3) {
				throw new IOException("Stuck in redirect loop");
			}
			URL resourceUrl = new URL(url);
			con = (HttpURLConnection) resourceUrl.openConnection();
			con.setConnectTimeout(15000);
			con.setReadTimeout(15000);
			con.setInstanceFollowRedirects(false); // Make the logic below easier to detect redirections

			switch (con.getResponseCode()) {
				case HttpURLConnection.HTTP_MOVED_PERM:
				case HttpURLConnection.HTTP_MOVED_TEMP:
					String location = con.getHeaderField("Location");
					location = URLDecoder.decode(location, "UTF-8");
					URL base = new URL(url);
					URL next = new URL(base, location); // Deal with relative URLs
					url = next.toExternalForm();
					continue;
			}
			break;
		}

		StringBuilder sb = new StringBuilder("");
		Scanner sc = null;
		try {
			sc = new Scanner(con.getInputStream());
			while (sc.hasNextLine()) {
				sb.append(sc.nextLine()).append("\n");
			}
		} finally {
			if (sc != null) {
				sc.close();
			}
		}
		return sb.toString();
	}
}
