package com.htm.jericho.parse;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.dom4j.Element;
import org.dom4j.Node;
import org.dom4j.VisitorSupport;
import org.dom4j.tree.DefaultAttribute;

/**
 * 
 * html解析成dom(Dom4j)
 * 
 * @author jwnie
 */
public class HtmlParser extends AbstractHtmlParser {
	protected static Log logger = LogFactory.getLog(HtmlParser.class);

	/**
	 * 需要过滤的节点集合
	 */
	protected List<String> filterNodes = new ArrayList<String>();

	public List<String> getFilterNodes() {
		return filterNodes;
	}

	public void setFilterNodes(List<String> filterNodes) {
		this.filterNodes = filterNodes;
	}

	@Override
	public boolean acceptNode(au.id.jericho.lib.html.Element htmlElement) {
		if (htmlElement.getName().equalsIgnoreCase("span")
				&& htmlElement.getAttributeValue("style") != null
				&& htmlElement.getAttributeValue("style").equalsIgnoreCase(
						"display:none")) {
			// 过滤baise.cc中的干扰码
			return false;
		}
		if (filterNodes.contains(htmlElement.getName().toLowerCase())) {
			return false;
		}
		return true;
	}

	@Override
	public boolean acceptNodeText(au.id.jericho.lib.html.Segment node) {
		return true;
	}

	/**
	 * 去除一段文字中的html标签
	 * 
	 * @param text
	 *            输入文本
	 * @return 过滤掉标签的文本
	 */
	protected String deleteElementsInText(String text) {
		int lpos = text.indexOf("<");// 记录<号的位置
		int rpos = -1;// 记录>号的位置
		String content = "";// 保存内容信息
		boolean MayBr = false;// 如果可以换行则为真，否则为否

		for (; lpos != -1;) {
			if (lpos > rpos + 1) {
				content = content + text.substring(rpos + 1, lpos);
				MayBr = true;
			}
			rpos = text.indexOf(">", lpos);
			if (rpos == -1) {
				logger.warn("[Extractor]: " + getClass().getName()
						+ " extract reply content failed");
				return content;
			}
			String tmp = text.substring(lpos + 1, rpos);// 保存<和>之间的文本
			// 添加换行
			if (MayBr) {

				if (tmp.equals("p") || tmp.equals("br") || tmp.equals("br/")) {
					content = content + "    ";// 换行替换为4个空格
					MayBr = false;
				}
			}
			lpos = text.indexOf("<", rpos);
		}
		content = content + text.substring(rpos + 1);
		return content;
	}

	public String filterAppointNoise(String value, String leftFlag,
			String rightFlag) {
		String[] lf = null;
		String[] rf = null;
		if (leftFlag != null && !leftFlag.isEmpty()) {
			lf = leftFlag.split("/");
		}
		if (rightFlag != null && !rightFlag.isEmpty()) {
			rf = rightFlag.split("/");
		}
		if (lf != null && rf != null) {
			if (lf.length == 1 && rf.length == 1) {
				leftFlag = leftFlag.replaceAll("&slash;", "/");
				rightFlag = rightFlag.replaceAll("&slash;", "/");
				return filterNoise(value, leftFlag, rightFlag);
			}
		}
		if (lf != null) {
			for (String s : lf) {
				s = s.replaceAll("&slash;", "/");
				value = filterNoise(value, s, null);
			}
		}
		if (rf != null) {
			for (String s : rf) {
				s = s.replaceAll("&slash;", "/");
				value = filterNoise(value, null, s);
			}
		}
		return value;
	}

	private String filterNoise(String value, String leftFlag, String rightFlag) {
		int leftIndex = 0;
		int rightIndex = 0;
		if (value != null && !value.isEmpty()) {
			if (leftFlag != null && !leftFlag.isEmpty()) {
				leftIndex = value.indexOf(leftFlag);
				if (rightFlag != null && !rightFlag.isEmpty()) {
					rightIndex = value.indexOf(rightFlag);
					if (rightIndex >= 0 && leftIndex >= 0
							&& rightIndex > leftIndex) {
						value = value.substring(leftIndex + leftFlag.length(),
								rightIndex).trim();
					} else if (rightIndex >= 0 && leftIndex < 0) {
						value = value.substring(0, rightIndex).trim();
					} else if (rightIndex < 0 && leftIndex >= 0) {
						value = value.substring(leftIndex + leftFlag.length())
								.trim();
					}
				} else {
					if (leftIndex >= 0) {
						value = value.substring(leftIndex + leftFlag.length())
								.trim();
					}
				}
			} else {
				if (rightFlag != null && !rightFlag.isEmpty()) {
					rightIndex = value.indexOf(rightFlag);
					if (rightIndex >= 0) {
						value = value.substring(0, rightIndex).trim();
					}
				}
			}
		}
		return value;
	}

	/**
	 * 提取一个节点元素内的所有文本内容，并且计算换行符
	 * 
	 * @param node
	 *            待提取文本内容的HTML转DOM对象
	 * @return 抽取后节点内的所有子节点的内容
	 */
	@SuppressWarnings("unchecked")
	public static String extractElementValue(final Element node,
			final List<String> unuseNodes) {
		if (node == null) {
			return "";
		}
		final List<Element> unuseElements = new ArrayList<Element>();
		if (!CollectionUtils.isEmpty(unuseNodes)) {
			for (String unuseXpath : unuseNodes) {
				unuseElements.addAll(node.selectNodes(unuseXpath));
			}
		}
		// 过滤js、html
		unuseElements.addAll(node.selectNodes(".//script"));
		unuseElements.addAll(node.selectNodes(".//style"));

		final StringBuffer buf = new StringBuffer();
		/**
		 * 
		 * @author unknown
		 * 
		 */
		class Visitor extends VisitorSupport {
			private boolean isEnd = false;

			private boolean isMayBr = false;
			private boolean flag = true;
			private List<Element> addlist = new ArrayList<Element>();

			public void visit(org.dom4j.Element node) {
				for (Element unuseElement : unuseElements) {
					if (unuseElement != null
							&& unuseElement.getUniquePath().equals(
									node.getUniquePath())) {
						return;
					}
				}

				String name = node.getName();
				if (name.equals("img")) {
					buf.append("img context");
					buf.append("\r\n");
				}
				if (name.equals("embed")) {
					buf.append("video or img context");
					buf.append("\r\n");
				}
				if (isMayBr && (name.equals("br") || name.equals("p"))) {
					isMayBr = false;
					buf.append("    ");
				}

				if (addlist.size() == 0 && flag) {
					flag = false;
					for (Element unuseElement : unuseElements) {
						List<Element> children = unuseElement.elements();
						for (Element child : children) {
							addlist.add(child);
						}
					}
					unuseElements.addAll(addlist);
				} else {
					List<Element> templist = new ArrayList<Element>();
					templist.addAll(addlist);
					addlist.clear();
					for (Element unuseElement : templist) {
						List<Element> children = unuseElement.elements();
						for (Element child : children) {
							addlist.add(child);
						}
					}
					unuseElements.addAll(addlist);
				}
			}

			public void visit(org.dom4j.Text node) {
				if (isEnd) {
					return;
				}

				for (Element unuseElement : unuseElements) {
					if (unuseElement != null
							&& unuseElement.getUniquePath().equals(
									node.getParent().getUniquePath())) {
						return;
					}
				}

				if (unuseNodes != null
						&& unuseNodes.contains(node.getParent().getName())) {
					return;
				}
				String text = node.getText().trim();
				if (text.length() < 1) {
					return;
				}
				buf.append(text);
				isMayBr = true;
			}
		}

		Visitor vis = new Visitor();
		node.accept(vis);
		return buf.toString();
	}

	/**
	 * 提取POSTDATA,对img标签特殊处理
	 * 
	 * @param contextNode
	 *            待提取文本内容的HTML转DOM对象
	 * @return 抽取后节点内的所有子节点的内容
	 */
	@SuppressWarnings("unchecked")
	public static String extractContextValue(final Node contextNode,
			final List<String> unuseNodes) {
		if (contextNode == null) {
			return "";
		}
		final List<Element> unuseElements = new ArrayList<Element>();
		if (!CollectionUtils.isEmpty(unuseNodes)) {
			for (String unuseXpath : unuseNodes) {
				unuseElements.addAll(contextNode.selectNodes(unuseXpath));
			}
		}

		// 过滤js、html
		unuseElements.addAll(contextNode.selectNodes(".//script"));
		unuseElements.addAll(contextNode.selectNodes(".//style"));

		final StringBuffer buf = new StringBuffer();
		/**
		 * 
		 * @author unknown
		 * 
		 */
		class Visitor extends VisitorSupport {
			private boolean isEnd = false;

			private boolean isMayBr = false;
			private boolean flag = true;
			private List<Element> addlist = new ArrayList<Element>();

			public void visit(org.dom4j.Element node) {
				String name = node.getName();
				if (unuseNodes != null) {
					for (String unuseXpath : unuseNodes) {
						List<Element> unuseList = (List<Element>) node
								.selectNodes(unuseXpath);
						if (CollectionUtils.isNotEmpty(unuseList)) {
							for (Element noiseNode : unuseList) {
								node.remove(noiseNode);
							}
						}
					}
				}

				// 处理img标签，获取图片地址
				if (name.equals("img")) {
					String alt = node.attributeValue("alt");
					if (StringUtils.isBlank(alt)) {
						alt = "";
					}
					List<DefaultAttribute> list = node.attributes();
					String imgUrl = null;
					String border = null;
					for (int i = 0; i < list.size(); i++) {
						DefaultAttribute attr = list.get(i);
						String attrName = attr.getName();
						String value = attr.getText();
						if (!value.toLowerCase().contains("jpg")
								&& !value.toLowerCase().contains("jpeg")) {
							continue;
						}

						if (value.length() <= 5) {
							continue;
						}

						if (attrName.equals("border")) {
							border = attr.getText();
						}

						if (attrName.contains("src")) {
							imgUrl = value;
						}

						if (value.startsWith("http")) {
							imgUrl = value;
							break;
						}

						if (StringUtils.isEmpty(imgUrl)) {
							imgUrl = value;
						}
					}

					// 过滤垃圾图片
					boolean isTrash = false;// 是否广告图片
					if ("0".equals(border) && list.size() < 4) {
						isTrash = true;
					} else if (StringUtils.isNotBlank(imgUrl)
							&& imgUrl.contains("/common/")) {
						isTrash = true;
					} else if (alt.contains("分享到")) {
						isTrash = true;
					}

					String title = node.attributeValue("title");
					if (title != null && title.contains("分享到")) {
						isTrash = true;
					}

					if (!isTrash && StringUtils.isNotBlank(imgUrl)) {
						buf.append("[img:" + imgUrl + ",alt=" + alt + "]");
					} else {
						buf.append("img context");
					}
					buf.append("\r\n");
				}
				if (name.equals("embed")) {
					buf.append("video or img context");
					buf.append("\r\n");
				}
				if (isMayBr && (name.equals("br") || name.equals("p"))) {
					isMayBr = false;
					buf.append("    ");
				}

				if (addlist.size() == 0 && flag) {
					flag = false;
					for (Element unuseElement : unuseElements) {
						List<Element> children = unuseElement.elements();
						for (Element child : children) {
							addlist.add(child);
						}
					}
					unuseElements.addAll(addlist);
				} else {
					List<Element> templist = new ArrayList<Element>();
					templist.addAll(addlist);
					addlist.clear();
					for (Element unuseElement : templist) {
						List<Element> children = unuseElement.elements();
						for (Element child : children) {
							addlist.add(child);
						}
					}
					unuseElements.addAll(addlist);
				}
			}

			public void visit(org.dom4j.Text node) {
				if (isEnd) {
					return;
				}

				for (Element unuseElement : unuseElements) {
					if (unuseElement != null
							&& unuseElement.getUniquePath().equals(
									node.getParent().getUniquePath())) {
						return;
					}
				}

				if (unuseNodes != null
						&& unuseNodes.contains(node.getParent().getName())) {
					return;
				}
				String text = node.getText().trim();
				if (text.length() < 1) {
					return;
				}
				buf.append(text);
				isMayBr = true;
			}
		}

		Visitor vis = new Visitor();
		contextNode.accept(vis);
		String context = buf.toString();
		if (StringUtils.isBlank(context)
				&& !CollectionUtils.isEmpty(unuseNodes)) {
			// 根据过滤规则过滤后正文为空，返回"."
			context = ".";
		}
		return context;
	}

}
