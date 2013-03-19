package com.sprocomm.systemupdate;

import java.io.InputStream;
import java.io.StringWriter;
import java.net.PasswordAuthentication;
import java.util.List;

import org.xml.sax.Parser;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlSerializer;

import android.util.Xml;

public class PullRomParser {
	public RomUtils parse(InputStream is) throws Exception {  
		RomUtils rom = new RomUtils();  

		//	      XmlPullParserFactory factory = XmlPullParserFactory.newInstance();  
		//	      XmlPullParser parser = factory.newPullParser();  

		XmlPullParser parser = Xml.newPullParser(); //由android.util.Xml创建一个XmlPullParser实例  
		parser.setInput(is, "UTF-8");               //设置输入流 并指明编码方式  

		int eventType = parser.getEventType();  
		while (eventType != XmlPullParser.END_DOCUMENT) {  
			switch (eventType) {  
			case XmlPullParser.START_DOCUMENT:  
				break;  
			case XmlPullParser.START_TAG:  
				if (parser.getName().equals("device")) {  
					eventType = parser.next();  
					rom.setDevice(parser.getText());
				} else if (parser.getName().equals("version")) {  
					eventType = parser.next();  
					rom.setVersion(parser.getText());  
				} else if (parser.getName().equals("build")) {  
					eventType = parser.next();  
					rom.setBuildTime(parser.getText());  
				} else if (parser.getName().equals("md5")) {  
					eventType = parser.next();  
					rom.setMD5(parser.getText());  
				}  else if (parser.getName().equals("downloadurl")){
					eventType = parser.next();  
					rom.setDownLoadUrl(parser.getText());  
				} else if (parser.getName().equals("filename")){
					eventType = parser.next();  
					rom.setFileName(parser.getText());
				}
				break;  
			case XmlPullParser.END_TAG:  
				break;  
			}  
			eventType = parser.next();  
		}  
		return rom;  
	}  

	public String serialize(RomUtils books) throws Exception {  
		//	      XmlPullParserFactory factory = XmlPullParserFactory.newInstance();  
		//	      XmlSerializer serializer = factory.newSerializer();  

		XmlSerializer serializer = Xml.newSerializer(); //由android.util.Xml创建一个XmlSerializer实例  
		StringWriter writer = new StringWriter();  
		serializer.setOutput(writer);   //设置输出方向为writer  
		serializer.startDocument("UTF-8", true);  
		serializer.startTag("", "books");  
		serializer.startTag("", "book");  
//		serializer.attribute("", "id", book.getId() + "");  
//
//		serializer.startTag("", "name");  
//		serializer.text(book.getName());  
//		serializer.endTag("", "name");  
//
//		serializer.startTag("", "price");  
//		serializer.text(book.getPrice() + "");  
		serializer.endTag("", "price");  

		serializer.endTag("", "book");  
		serializer.endTag("", "books");  
		serializer.endDocument();  

		return writer.toString();  
	}  
}
