//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by Fernflower decompiler)
//

package io.manebot.plugin.music.source;

import org.apache.commons.io.*;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.nio.ByteBuffer;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class RangedInputStream extends InputStream {
    private static final Pattern rangePattern = Pattern.compile("(\\w+)\\s(\\d+)-(\\d+)\\/([\\d*]+)");
    private final URL url;
    private final Map<String, String> requestProperties;
    private final int bufferSize;
    private InputStream current;
    private Long contentLength;
    private Boolean supportsRangeRequests;
    private long position;
    
    public RangedInputStream(URL url, Map<String, String> requestProperties, int bufferSize) {
	this.url = url;
	this.requestProperties = requestProperties;
	this.bufferSize = bufferSize;
    }
    
    private URLConnection openBasicConnection() throws IOException {
	URLConnection connection = this.url.openConnection();
	this.requestProperties.forEach(connection::setRequestProperty);
	return connection;
    }
    
    private InputStream createBufferedStream(InputStream inputStream, int chunkSize) {
        return new BufferedInputStream(inputStream, chunkSize);
    }
    
    private InputStream next(int chunkSize) throws IOException {
	URLConnection connection = this.openBasicConnection();
	if (connection instanceof HttpURLConnection) {
	    HttpURLConnection httpURLConnection = (HttpURLConnection)connection;
	    httpURLConnection.setRequestMethod("GET");
	    if (this.supportsRangeRequests != null && this.supportsRangeRequests) {
		httpURLConnection.setRequestProperty(
				"Range",
				"bytes=" + this.position + "-" + (Math.min(this.position + (long) chunkSize, this.contentLength) - 1)
		);
	    }
	    
	    if (httpURLConnection.getResponseCode() / 100 == 2) {
		switch(httpURLConnection.getResponseCode()) {
		    case 200:
			this.contentLength = httpURLConnection.getContentLengthLong();
			
			if (this.contentLength == 0L)
			    throw new EOFException();
			else if (this.contentLength < 0L)
			    throw new IOException("Invalid content length: " + this.contentLength);
			
			if (this.supportsRangeRequests == null && httpURLConnection.getHeaderField("accept-ranges") != null) {
			    this.supportsRangeRequests = httpURLConnection.getHeaderField("accept-ranges").equalsIgnoreCase("bytes");
			    if (this.supportsRangeRequests) {
				httpURLConnection.disconnect();
				return next(chunkSize);
			    }
			}
			
			if (this.position != 0L) {
			    throw new IOException("Unexpected content start at position 0 when content position is " + this.position);
			}
		 
			return createBufferedStream(connection.getInputStream(), chunkSize);
		    case 204:
			throw new EOFException("HTTP 204 No Content");
		    case 206:
			Matcher matcher = rangePattern.matcher(httpURLConnection.getHeaderField("content-range"));
			if (!matcher.find()) {
			    throw new IOException("Unexpected Content-Range: \"" + httpURLConnection.getHeaderField("content-range") + "\"");
			}
			
			String type = matcher.group(1);
			if (!type.equalsIgnoreCase("bytes")) {
			    throw new IOException("Unexpected content range response type: " + type);
			}
			
			long start = Long.parseLong(matcher.group(2));
			if (start != this.position) {
			    throw new IOException("Expected ranged start at position " + this.position +
					    ", but server sent range starting at position " + start);
			}
			
			long end = Long.parseLong(matcher.group(3));
			long length = (end - start) + 1;
			if (length < 0L)
			    throw new IOException("Invalid length: " + length);
			else if (length == 0)
			    throw new EOFException();
			
			if (length != httpURLConnection.getContentLengthLong()) {
			    throw new EOFException("Mismatched range request response size and content length: " +
					    length + " != " + httpURLConnection.getContentLengthLong());
			}
			
			String totalSizeString = matcher.group(4);
			
			try {
			    this.contentLength = Long.parseLong(totalSizeString);
			} catch (NumberFormatException ex) {
			    if (contentLength == null)
			        throw new IOException("Problem reading content length", ex);
			}
		 
			if (position + length > this.contentLength)
			    throw new IOException("Unexpected content length: " + (position + length) + " > " + this.contentLength);
			
			return createBufferedStream(connection.getInputStream(), (int) length);
		}
	    }
	    
	    throw new IOException(this.url.toExternalForm() + " returned HTTP " + httpURLConnection.getResponseCode()
			    + " " + httpURLConnection.getResponseMessage());
	} else {
	    return createBufferedStream(connection.getInputStream(), chunkSize);
	}
    }
    
    @Override
    public int available() throws IOException {
	return current != null ? current.available() : 0;
    }
    
    @Override
    public int read() throws IOException {
	throw new IOException(new UnsupportedOperationException());
    }
    
    @Override
    public int read(byte[] buffer, int offs, int len) throws IOException {
	if (this.contentLength != null && this.position >= this.contentLength) {
	    return -1;
	}
 
	int position = 0;
	while (position < len) {
	    if (contentLength != null && this.position >= this.contentLength)
	        return position > 0 ? position : -1;
	    
	    if (this.current == null) {
		try {
		    this.current = this.next(this.bufferSize);
		} catch (EOFException var2) {
		    return -1;
		}
	    }
	    
	    int read = this.current.read(buffer, position + offs, len - position);
	    
	    if (read > 0) {
		position += read;
		this.position += read;
	    } else if (read == -1) {
		this.current.close();
		this.current = null;
	    } else {
	        throw new IOException("Unexpected read size: " + read);
	    }
	}
	
	return position;
    }
}
