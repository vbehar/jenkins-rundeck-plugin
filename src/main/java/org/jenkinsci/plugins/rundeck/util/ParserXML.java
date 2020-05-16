package org.jenkinsci.plugins.rundeck.util;

import org.dom4j.Document;
import org.dom4j.DocumentException;
import org.dom4j.Node;
import org.dom4j.io.SAXReader;
import org.rundeck.api.RundeckApiException;
import org.xml.sax.SAXException;
import java.io.InputStream;

public class ParserXML {

    public static Document loadDocument(InputStream inputStream) throws RundeckApiException {

        SAXReader reader = new SAXReader();

        try {
            reader.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            reader.setFeature("http://xml.org/sax/features/external-general-entities", false);
            reader.setFeature("http://xml.org/sax/features/external-parameter-entities", false);

        } catch (SAXException e) {
            throw new RundeckApiException(e.getMessage());
        }

        reader.setEncoding("UTF-8");

        Document document;
        try {
            document = reader.read(inputStream);
        } catch (DocumentException ex) {
            throw new RundeckApiException("Failed to read Rundeck response: " + ex.getMessage());
        }

        document.setXMLEncoding("UTF-8");
        Node result = document.selectSingleNode("result");
        if (result != null) {
            Boolean failure = Boolean.valueOf(result.valueOf("@error"));
            if (failure) {
                throw new RundeckApiException(result.valueOf("error/message"));
            }
        }

        return document;
    }
}
