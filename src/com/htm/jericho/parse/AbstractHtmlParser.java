package com.htm.jericho.parse;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.dom4j.Document;
import org.dom4j.DocumentHelper;
import org.dom4j.tree.DefaultAttribute;

import au.id.jericho.lib.html.Attribute;
import au.id.jericho.lib.html.Attributes;
import au.id.jericho.lib.html.CharacterReference;
import au.id.jericho.lib.html.Element;
import au.id.jericho.lib.html.HTMLElements;
import au.id.jericho.lib.html.MasonTagTypes;
import au.id.jericho.lib.html.MicrosoftTagTypes;
import au.id.jericho.lib.html.Segment;
import au.id.jericho.lib.html.Source;
import au.id.jericho.lib.html.StartTag;
import au.id.jericho.lib.html.Tag;

/**
 * HTML页面的解析抽象接口
 * @author Jwnie
 * 
 */
public abstract class AbstractHtmlParser extends AbstractParser {
	protected static Log logger = LogFactory.getLog(AbstractHtmlParser.class);

	/**
	 * 构建的
	 */
	protected final static String ROOT_ELEMENT_NAME = "document";

	protected final static HashSet<String> HTML_FLAG_SET = buildHtmlFlagSet();

	protected static Pattern _styleDisplayNonePattern = null;

	protected String _title = "";

	protected String _meta = "";

	protected Document _doc = null;

	protected Source _source = null;

	protected String curUrl = null;

	protected HttpClient httpClient = null;

	/**
	 * 存储文本节点信息，利用这些节点的偏移量作为索引
	 */
	protected HashMap<Integer, String> _textMap = new HashMap<Integer, String>();

	@SuppressWarnings("unchecked")
	private static HashSet<String> buildHtmlFlagSet() {
		List<String> list = HTMLElements.getElementNames();
		HashSet<String> flagSet = new HashSet<String>(list.size());
		for (String name : list) {
			flagSet.add(name);
		}
		return flagSet;
	}

	public void parse(final String url) throws ParserException {
		try {
			parse(new URL(url));
		} catch (Exception e) {
			throw new ParserException(e);
		}
	}

	public void parse(final URL url) throws ParserException {
		try {
			parse(url.openConnection().getInputStream());
		} catch (IOException e) {
			throw new ParserException(e);
		}
	}

	public void parse(final InputStream in) throws ParserException {
		try {
			MicrosoftTagTypes.register();
			MasonTagTypes.register();
			_source = new Source(in);
			_source.fullSequentialParse();
			buildTextIndex();
			buildDoc();
		} catch (IOException e) {
			throw new ParserException(e);
		}
	}

	public void parse(CharSequence text) throws ParserException {
		MicrosoftTagTypes.register();
		MasonTagTypes.register();
		_source = new Source(text);
		_source.fullSequentialParse();
		buildTextIndex();
		buildDoc();
	}

	/**
	 * 用指定的编码对流进行编译操作，避免部分页面没有meta标签导致返回数据乱码的问题
	 * 
	 * @param in
	 *            待处理流
	 * @param charset
	 *            字符编码集
	 * @throws ParseException
	 */
	public void parse(InputStream in, String charset) throws ParserException {
		StringBuffer sb = readInputStream(in, charset);
		if (sb == null || StringUtils.isEmpty(sb.toString())) {
			return;
		}
		String str = sb.toString();
		str = str.replace("(?is)<script.*?>.*?</script>", "");
		str = str.replace("(?is)<style.*?>.*?</style>", "");
		parse(str.subSequence(0, str.length() - 1));
	}

	@SuppressWarnings("unchecked")
	private void buildTextIndex() {
		for (Iterator<Segment> i = _source.getNodeIterator(); i.hasNext();) {
			Segment node = i.next();
			if (node instanceof Tag) {
				Tag tag = (Tag) node;
				if (tag.getTagType().isServerTag())
					continue; // ignore server tags
			} else {
				String text = CharacterReference.decodeCollapseWhiteSpace(node);
				if (text != null && text.trim().length() > 0) {
					String content = AbstractParser.earseISOControlChar(text);
					if (content.length() < 1)
						continue;
					if (this.acceptNodeText(node)) {
						this._textMap.put(node.getBegin(), content);
					} else {
						continue;
					}
				}
			}
		}
	}

	/**
	 * 处理是否需要提取这个标记段的text内容，此处的节点由于html的特殊性，采用的是标记段的方式
	 * 
	 * @param node
	 *            一个标记节点
	 * @return 需要保留，返回true,否则返回false
	 */
	public abstract boolean acceptNodeText(Segment node);

	@SuppressWarnings("unchecked")
	private void buildDoc() {
		_doc = DocumentHelper.createDocument();
		org.dom4j.Element domElement = _doc.addElement(ROOT_ELEMENT_NAME);
		// TODO StackOverflowError
		List<Element> list = _source.getChildElements();

		for (Element htmlElement : list) {
			visitNodes(htmlElement, domElement);
		}
	}

	@SuppressWarnings("unchecked")
	private void visitNodes(final Element htmlElement,
			org.dom4j.Element domElement) {
		if (!acceptNode(htmlElement))
			return;

		org.dom4j.Element htmlDocElement = html2doc(domElement, htmlElement);

		if (isScriptStyleFlag(htmlElement)) {
			htmlDocElement.setText(htmlElement.getContent().toString());
		}
		// 处理title
		if (htmlElement.getName() == HTMLElements.TITLE) {
			String text = htmlElement.getTextExtractor().toString();
			if (text != null && text.length() > this._title.length()) {
				this._title = text;
			}
			if (text != null)
				htmlDocElement.setText(this._title);
			return;
		}
		// 处理meta
		if (htmlElement.getName() == HTMLElements.META) {
			String name = htmlElement.getAttributeValue("name");
			if (name != null && name.equalsIgnoreCase("KEYOWRDS")) {
				this._meta = htmlElement.getAttributeValue("content");
			}
			return;
		}

		// 处理text数据，由于html的tag是不完全的，需要分类处理
		// 这里不区分闭合与非闭合标签，只区分谁更近一些
		StartTag stag = htmlElement.getStartTag();
		if (this._textMap.containsKey(stag.getEnd())) {// 有text
			htmlDocElement.setText(this._textMap.get(stag.getEnd()));
		}

		List<Element> list = htmlElement.getChildElements();
		if (list.isEmpty())
			return;
		for (Element e : list) {
			visitNodes(e, htmlDocElement);
			if (e.getStartTag().isEndTagRequired()
					&& this._textMap.containsKey(e.getEnd())) {
				htmlDocElement.addText(this._textMap.get(e.getEnd()));
			}
		}
	}

	@SuppressWarnings("unchecked")
	private org.dom4j.Element html2doc(org.dom4j.Element domElement,
			final Element htmlElement) {
		org.dom4j.Element domHtmlElement = domElement
				.addElement(earseErrorDocNameCharset(htmlElement.getName()));
		Attributes attributes = htmlElement.getAttributes();
		if (attributes != null && !attributes.isEmpty()) {
			for (Iterator<Attribute> i = (Iterator<Attribute>) attributes
					.iterator(); i.hasNext();) {
				Attribute attribute = i.next();
				domHtmlElement.addAttribute(
						earseErrorDocNameCharset(attribute.getName()),
						attribute.getValue());
			}
		}
		return domHtmlElement;
	}

	/**
	 * 是否为html结束标记
	 * 
	 * @param name
	 *            标记的名称
	 * @return true 是html结束标记，false 不是
	 */
	public static boolean isHtmlElementFlag(final String name) {
		return HTML_FLAG_SET.contains(name);
	}

	/**
	 * 是否为script标记
	 * 
	 * @param htmlElement
	 *            html元素
	 * @return true 是script标记，false 不是
	 */
	public static boolean isScriptStyleFlag(final Element htmlElement) {
		String name = htmlElement.getName();
		if (name.equals(Element.SCRIPT) || name.equals(Element.STYLE)) {
			return true;
		}
		return false;
	}

	/**
	 * 判断元素是否为超链接标记
	 * 
	 * @param htmlElement
	 *            html元素
	 * @return true:是超链接，false:不是
	 */
	public static boolean isLinkElement(final Element htmlElement) {
		String name = htmlElement.getName();
		if (name.equals(Element.A)
				&& htmlElement.getAttributeValue("href") != null) {
			return true;
		}
		if (name.equals(Element.AREA)
				&& htmlElement.getAttributeValue("href") != null) {
			return true;
		}
		return false;
	}

	/**
	 * 评定一个节点是否其内部样式定义了为display:none
	 * 
	 * @param htmlElement
	 *            待评定的html节点
	 * @return true:存在display:none，false：不存在
	 */
	public static boolean isStyleNoneDisplay(final Element htmlElement) {
		if (_styleDisplayNonePattern == null) {
			_styleDisplayNonePattern = Pattern.compile(
					"(display:\\s*none)|(font-size:\\s*0px)",
					Pattern.CASE_INSENSITIVE);
		}
		String style = htmlElement.getAttributeValue("style");
		if (style != null && style.length() > 10) {
			Matcher matcher = _styleDisplayNonePattern.matcher(style);
			return matcher.find();
		}
		return false;
	}

	/**
	 * 过滤title中的噪音信息，仅提取关键的标题信息
	 * 
	 * @param title
	 *            已知的标题
	 * @return 消除噪音后的标题信息
	 */
	public static String filterTitleNoise(String title) {
		return title;
	}

	public String getEncoding() {
		return _source.getEncoding();
	}

	public String getText() {
		return this._source.getTextExtractor().toString();
	}

	@SuppressWarnings("unchecked")
	public List<String> getUrls() {
		ArrayList<String> list = new ArrayList<String>();
		List<DefaultAttribute> elements = this._doc.selectNodes("//@href");
		for (DefaultAttribute element : elements) {
			String url = element.getText();
			if (isJunkUrl(url)) {
				continue;
			}
			list.add(url);
		}
		elements = this._doc.selectNodes("//@HREF");
		for (DefaultAttribute element : elements) {
			String url = element.getText();
			if (isJunkUrl(url)) {
				continue;
			}
			list.add(url);
		}
		return list;
	}

	private boolean isJunkUrl(String url) {
		if (url.equals("") || url.length() < 1) {
			return true;
		}
		if (url.startsWith("#")) {
			return true;
		}
		if (url.startsWith("javascript")) {
			return true;
		}
		if (url.startsWith("mailto")) {
			return true;
		}
		return false;
	}

	public String assembledUrl(String originalUrl, String oppositeUrl) {
		if (oppositeUrl.startsWith("http:") || oppositeUrl.startsWith("ftp:")) {
			return oppositeUrl;
		} else if (oppositeUrl.startsWith("../")) {
			int flag = 0;
			while (oppositeUrl.indexOf("../") != -1) {
				oppositeUrl = oppositeUrl.substring(3);
				flag++;
			}
			for (int i = 0; i <= flag; i++) {
				int index = originalUrl.lastIndexOf("/");
				if (index != -1) {
					originalUrl = originalUrl.substring(0, index);
				}
			}
			return originalUrl + "/" + oppositeUrl;
		} else if (oppositeUrl.startsWith("/")) {
			int lf = originalUrl.indexOf("//");
			if (lf != -1) {
				int rf = originalUrl.indexOf("/", lf + 2);
				if (rf != -1) {
					originalUrl = originalUrl.substring(0, rf);
				}
			}
			return originalUrl + oppositeUrl;
		} else {
			int index = originalUrl.lastIndexOf("/");
			if (index != -1) {
				originalUrl = originalUrl.substring(0, index + 1);
			}
			if (oppositeUrl.startsWith("./")) {
				return originalUrl + oppositeUrl.substring(2);
			} else {
				return originalUrl + oppositeUrl;
			}
		}
	}

	public Document getDoc() {
		return _doc;
	}

	public String getCurUrl() {
		return curUrl;
	}

	public void setCurUrl(String curUrl) {
		this.curUrl = curUrl;
	}

	public HttpClient getHttpClient() {
		return httpClient;
	}

	public void setHttpClient(HttpClient httpClient) {
		this.httpClient = httpClient;
	}

	@Override
	public String getDocFile(final String fileName, String charset) {
		AbstractParser.xmlDocWrite(this._doc, fileName, charset);
		return fileName;
	}

	public void free() {
		this._source.clearCache();
		this._doc.clearContent();
	}

	/**
	 * 在解析HTML并且处理那些错误页面的时候，校验那些节点是需要的，那些节点是不需要的
	 * 
	 * @param htmlElement
	 *            HTML节点元素，节点操作方法，请参见jericho的Element说明
	 * @return 返回true，那么该节点保留；返回false，那么该节点丢弃
	 */
	public abstract boolean acceptNode(final Element htmlElement);

	/**
	 * 将流数据转换成StringBuffer
	 * 
	 * @param in
	 *            要转换的流
	 * @param charset
	 *            流的字符集
	 * @return 转换后的字符串
	 */
	protected StringBuffer readInputStream(InputStream in, String charset) {
		InputStreamReader isr = null;
		BufferedReader br = null;
		StringBuffer sb = new StringBuffer();
		try {
			isr = new InputStreamReader(in, charset);
			br = new BufferedReader(isr);
			String str = br.readLine();
			while (str != null) {
				sb.append(str);
				str = br.readLine();
			}
		} catch (IOException e) {
			logger.warn("Extract inputStream error! continue");
		} finally {
			if (br != null) {
				try {
					br.close();
				} catch (IOException e) {
				}
				;
			}
			if (isr != null) {
				try {
					isr.close();
				} catch (IOException e) {
				}
			}
		}
		return sb;
	}

	public String get_title() {
		return _title;
	}

	public void set_title(String _title) {
		this._title = _title;
	}

}
