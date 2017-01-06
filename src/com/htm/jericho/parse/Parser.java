package com.htm.jericho.parse;

import java.io.InputStream;
import java.net.URL;
import java.util.List;

import org.dom4j.Document;

/**
 * htmparser interface
 * @author Jwnie
 *
 */
public interface Parser {
	
	public void parse(final String url) throws ParserException;

	public void parse(final URL url) throws ParserException;

	public void parse(final InputStream in) throws ParserException;

	public void parse(CharSequence text) throws ParserException;

	public void parse(InputStream in, String charset) throws ParserException;

	public String getEncoding();

	public String getText();

	public List<String> getUrls();

	public String assembledUrl(String originalUrl, String oppositeUrl);

	public Document getDoc();
	
	public String getDocFile(final String fileName, String charset);
	
	public void free();
}
