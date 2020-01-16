//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by Fernflower decompiler)
//

package io.manebot.plugin.music.source;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
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
    private final ByteBuffer buffer;
    private InputStream current;
    private Long contentLength;
    private Boolean supportsRangeRequests;
    private long position;
    
    public RangedInputStream(URL url, Map<String, String> requestProperties, int bufferSize) {
	this.url = url;
	this.requestProperties = requestProperties;
	this.bufferSize = bufferSize;
	this.buffer = ByteBuffer.allocate(bufferSize).limit(0);
    }
    
    private URLConnection openBasicConnection() throws IOException {
	URLConnection connection = this.url.openConnection();
	this.requestProperties.forEach(connection::setRequestProperty);
	return connection;
    }
    
    private InputStream next() throws IOException {
	URLConnection connection = this.openBasicConnection();
	if (connection instanceof HttpURLConnection) {
	    HttpURLConnection httpURLConnection = (HttpURLConnection)connection;
	    httpURLConnection.setRequestMethod("GET");
	    if (this.supportsRangeRequests != null && this.supportsRangeRequests) {
		httpURLConnection.setRequestProperty(
				"Range",
				"bytes=" + this.position + "-" + Math.min(this.position + (long) this.bufferSize, this.contentLength)
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
				return next();
			    }
			}
			
			if (this.position != 0L) {
			    throw new IOException("Unexpected content start at position 0 when content position is " + this.position);
			}
		 
			return connection.getInputStream();
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
			long length = end - start;
			if (length < 0L)
			    throw new IOException("Invalid length: " + length);
			else if (length == 0)
			    throw new EOFException();
			
			if (length != httpURLConnection.getContentLengthLong() - 1L) {
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
			
			return connection.getInputStream();
		}
	    }
	    
	    throw new IOException(this.url.toExternalForm() + " returned HTTP " + httpURLConnection.getResponseCode()
			    + " " + httpURLConnection.getResponseMessage());
	} else {
	    return connection.getInputStream();
	}
    }
    
    public int available() {
	return this.buffer.remaining();
    }
    
    public int read() throws IOException {
	if (this.contentLength != null && this.position == this.contentLength) {
	    return -1;
	} else {
	    if (!this.buffer.hasRemaining()) {
		if (this.current == null) {
		    try {
			this.current = this.next();
		    } catch (EOFException var2) {
		        var2.printStackTrace();
			return -1;
		    }
		}
		
		this.buffer.position(0);
		this.buffer.limit(this.bufferSize);
		int b = -1;
		
		while(this.buffer.hasRemaining() && (b = this.current.read()) >= 0) {
		    this.buffer.put((byte)b);
		}
		
		if (b == -1) {
		    this.current.close();
		    this.current = null;
		}
		
		this.buffer.flip();
		if (!this.buffer.hasRemaining()) {
		    return -1;
		}
	    }
	    
	    ++this.position;
	    return this.buffer.get() & 255;
	}
    }
}
