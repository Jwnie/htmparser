package com.htmparser.test;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.commons.io.IOUtils;
import org.dom4j.Document;
import org.dom4j.Node;

import com.htm.jericho.parse.HtmlParser;

public class TestDemo {

	@org.junit.Test
	public void testHtmParser(){
		String url = "http://china.huanqiu.com/article/2017-01/9915116.html";
		HttpClient client = new HttpClient();
		GetMethod get = new GetMethod(url);
		InputStream in = null;
		try {
			client.executeMethod(get);
			HtmlParser hp = new HtmlParser();
			in = get.getResponseBodyAsStream();
			//解析成doc(Dom4j)
			hp.parse(in);
			Document doc = hp.getDoc();//
			System.out.println("--------Doc:----------");
			System.out.println(doc.asXML());
			
			Node titleNode = doc.selectSingleNode("//h1");
			Node contextNode  = doc.selectSingleNode("//div[@id='text']");
			Node timeNode = doc.selectSingleNode("//strong[@id='pubtime_baidu']");
			
			List<String> junkNodeList = new ArrayList<String>();
			junkNodeList.add("//script");
			junkNodeList.add("//div[@class='reTopics']");
			
			String context = hp.extractContextValue(contextNode, junkNodeList);
			
			StringBuilder sb = new StringBuilder();
			sb.append("-------------------------------------------------------");
			sb.append("Title:").append(titleNode.getStringValue()).append("\n");
			sb.append("PostTime:").append(timeNode.getStringValue()).append("\n");
			sb.append("Context:").append(context).append("\n");
			sb.append("-------------------------------------------------------");
			System.out.println(sb.toString());
			
		} catch (Exception e) {
			e.printStackTrace();
		}finally{
			IOUtils.closeQuietly(in);
			if(get != null){
				get.releaseConnection();
			}
			if(client != null){
				client.getHttpConnectionManager().closeIdleConnections(0);
			}
		}
	}
}
