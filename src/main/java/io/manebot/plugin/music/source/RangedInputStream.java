//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by Fernflower decompiler)
//

package io.manebot.plugin.music.source;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.util.Map;
import java.util.logging.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class RangedInputStream extends InputStream {
    private static final int EOF = -1;
    private static final Pattern rangePattern = Pattern.compile("(\\w+)\\s(\\d+)-(\\d+)\\/([\\d*]+)");
    private final URL url;
    private final Map<String, String> requestProperties;
    private final int bufferSize;
    private Long contentLength;
    private Boolean supportsRangeRequests;
    
    private long chunkPosition;
    private BufferedInputStream current;
    private long chunkLength;
    
    private long contentPosition;
    
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
    
    private BufferedInputStream createBufferedStream(InputStream inputStream, int chunkSize) {
        return new BufferedInputStream(inputStream, chunkSize);
    }
    
    private BufferedInputStream next(int chunkSize) throws IOException {
	URLConnection connection = this.openBasicConnection();
	if (connection instanceof HttpURLConnection) {
	    HttpURLConnection httpURLConnection = (HttpURLConnection)connection;
	    httpURLConnection.setRequestMethod("GET");
	    if (this.supportsRangeRequests != null && this.supportsRangeRequests) {
		httpURLConnection.setRequestProperty(
				"Range",
				"bytes=" + this.contentPosition + "-" + (Math.min(this.contentPosition + (long) chunkSize, this.contentLength) - 1)
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
			
			if (this.contentPosition != 0L) {
			    throw new IOException("Unexpected content start at position 0 when content position is " + this.contentPosition);
			}
		 
			BufferedInputStream wholeBis = createBufferedStream(connection.getInputStream(), chunkSize);
			this.chunkLength = this.contentLength;
			this.chunkPosition = 0L;
			return wholeBis;
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
			if (start != this.contentPosition) {
			    throw new IOException("Expected ranged start at position " + this.contentPosition +
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
		 
			if (contentPosition + length > this.contentLength)
			    throw new IOException("Unexpected content length: " + (contentPosition + length) + " > " + this.contentLength);
			
			BufferedInputStream rangeBis = createBufferedStream(connection.getInputStream(), (int) length);
			this.chunkLength = length;
			this.chunkPosition = 0L;
			return rangeBis;
		}
	    }
	    
	    throw new IOException(this.url.toExternalForm() + " returned HTTP " + httpURLConnection.getResponseCode()
			    + " " + httpURLConnection.getResponseMessage());
	} else {
	    BufferedInputStream bis = createBufferedStream(connection.getInputStream(), (int) chunkSize);
	    this.chunkLength = -1L;
	    this.chunkPosition = 0L;
	    return bis;
	}
    }
    
    @Override
    public int available() throws IOException {
	return current != null ? current.available() : 0;
    }
    
    @Override
    public int read() throws IOException {
	throw new UnsupportedOperationException();
    }
    
    @Override
    public int read(byte[] buffer, int offs, int len) throws IOException {
	if (this.contentLength != null && this.contentPosition >= this.contentLength) {
	    return -1;
	}
 
	int position = 0;
	while (position < len) {
	    if (this.contentLength != null && this.contentPosition >= this.contentLength)
	        return position > 0 ? position : -1;
	    
	    if (this.current == null) {
		try {
		    this.current = this.next(this.bufferSize);
		} catch (EOFException var2) {
		    return -1;
		}
	    }
	    
	    int read = len - position;
	    if (this.chunkLength > 0)
	        read = Math.min(read, (int) (this.chunkLength - this.chunkPosition));
	    
	    try {
		read = this.current.read(buffer, position + offs, read);
	    } catch (EOFException ex) {
	        read = -1;
	    } catch (IOException ex) {
		Logger.getGlobal().log(Level.WARNING, "Broken stream while reading " + read + " bytes from " + this.current
				+ " [" + chunkPosition + "/" + chunkLength + "]", ex);
	   
		try {
		    this.current.close();
		} catch (IOException suppress) {
		    ex.addSuppressed(suppress);
		}
	 
		this.current = null;
	        continue;
	    }
	    
	    if (read > 0) {
		position += read;
		this.contentPosition += read;
		this.chunkPosition += read;
	    }
	    
	    if (read == EOF || (this.chunkLength >= 0L && this.chunkPosition >= this.chunkLength)) {
	        try {
		    this.current.close();
		} catch (IOException ignored) {
	 
		}
	 
		this.current = null;
	    }
	}
	
	return position;
    }
}
