package com.htm.jericho.parse;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.dom4j.Attribute;
import org.dom4j.Document;
import org.dom4j.Element;
import org.dom4j.VisitorSupport;
import org.dom4j.io.OutputFormat;
import org.dom4j.io.XMLWriter;
import org.mozilla.intl.chardet.nsDetector;
import org.mozilla.intl.chardet.nsICharsetDetectionObserver;
import org.mozilla.intl.chardet.nsPSMDetector;

/**
 * 
 * @author Jwnie
 *
 */
public abstract class AbstractParser implements Parser {
	protected final static Log logger = LogFactory.getLog(AbstractParser.class);

	public static boolean xmlDocWrite(Document doc, String fileName,
			String charset) {
		FileOutputStream out = null;
		try {
			out = new FileOutputStream(fileName);
			OutputFormat format = OutputFormat.createPrettyPrint();
			format.setEncoding(charset);
			OutputStreamWriter outWriter = new OutputStreamWriter(out, charset);
			XMLWriter xmlOut = new XMLWriter(outWriter, format);
			xmlOut.write(doc);
			xmlOut.close();
		} catch (IOException e) {
			return false;
		} finally {
			if (out != null) {
				try {
					out.close();
				} catch (IOException e) {
					logger.error("save doc file failed:  ", e);
				}
			}
		}
		return true;
	}

	/**
	 * 返回非ISO控制字符
	 * @param str
	 * @return
	 */
	public static String earseISOControlChar(String str) {
		StringBuilder sb = new StringBuilder();
		if (str == null || "".equals(str)) {
			return "";
		} else {
			str = str.trim();
			for (int i = 0; i < str.length(); i++) {
				if (!Character.isISOControl(str.charAt(i))) {
					sb.append(str.charAt(i));
				}
			}
			return sb.toString();
		}
	}

	public static String earseErrorDocNameCharset(String name) {
		StringBuilder sb = new StringBuilder();
		char chr = name.charAt(0);
		if (!Character.isLetter(name.charAt(0)) && name.charAt(0) != '_') {
			sb.append("_");
		} else {
			sb.append(chr);
		}
		for (int i = 1; i < name.length(); i++) {
			chr = name.charAt(i);
			if (!Character.isLetter(chr) && !Character.isDigit(chr)
					&& chr != '.' && chr != '-' && chr != '_') {
				sb.append('_');
			} else {
				sb.append(chr);
			}
		}
		return sb.toString();
	}

	public String detCharset(final byte[] data, int len) {
		boolean isAscii = true;
		String encoding = null;
		CharSetNotify notify = new CharSetNotify();
		nsDetector det = new nsDetector(nsPSMDetector.ALL);
		det.Init(notify);
		// Check if the stream is only ascii.
		if (isAscii) {
			isAscii = det.isAscii(data, len);
		}
		// DoIt if non-ascii and not done yet.
		if (!isAscii) {
			det.DoIt(data, len, false);
		}
		det.DataEnd();
		if (isAscii) {
			encoding = "ASCII";
			return encoding;
		}
		if (!notify.found) {
			String prob[] = det.getProbableCharsets();
			if (prob != null && prob.length > 0) {
				encoding = prob[0];
			}
		} else {
			encoding = notify.encoding;
		}
		return encoding;
	}

	public void debugPringDoc(Document doc) {
		class MyVisitor extends VisitorSupport {
			public void visit(Element element) {
				System.out.println(element.getName() + "#$:"
						+ element.getText());
			}

			public void visit(Attribute attr) {
				System.out.println("--" + attr.getName() + "#$:"
						+ attr.getValue());
			}
		}
		MyVisitor visitor = new MyVisitor();
		doc.accept(visitor);
	}
}

class CharSetNotify implements nsICharsetDetectionObserver {
	public boolean found = false;

	public String encoding = null;

	public void Notify(String detCharSet) {
		found = true;
		encoding = detCharSet;
	}
}