/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-04 Wolfgang M. Meier
 *  wolfgang@exist-db.org
 *  http://exist-db.org
 *
 *  This program is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Lesser General Public License
 *  as published by the Free Software Foundation; either version 2
 *  of the License, or (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Lesser General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this program; if not, write to the Free Software
 *  Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
 * 
 *  $Id$
 */
package org.exist.storage.serializers;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.exist.dom.AttrImpl;
import org.exist.dom.CommentImpl;
import org.exist.dom.DocumentImpl;
import org.exist.dom.DocumentTypeImpl;
import org.exist.dom.ElementImpl;
import org.exist.dom.Match;
import org.exist.dom.NodeImpl;
import org.exist.dom.NodeProxy;
import org.exist.dom.ProcessingInstructionImpl;
import org.exist.dom.QName;
import org.exist.dom.TextImpl;
import org.exist.dom.XMLUtil;
import org.exist.storage.DBBroker;
import org.exist.util.Configuration;
import org.exist.util.FastQSort;
import org.exist.util.XMLString;
import org.exist.util.serializer.AttrList;
import org.exist.util.serializer.Receiver;
import org.exist.xquery.value.Type;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

/**
 * Serializer implementation for the native database backend.
 * 
 * @author wolf
 */
public class NativeSerializer extends Serializer {

    public final static int EXIST_ID_NONE = 0;

    public final static int EXIST_ID_ELEMENT = 1;

    public final static int EXIST_ID_ALL = 2;

    // private final static AttributesImpl EMPTY_ATTRIBUTES = new AttributesImpl();
    
    private final static QName MATCH_ELEMENT = new QName("match", EXIST_NS, "exist");
    private final static QName TEXT_ELEMENT = new QName("text", EXIST_NS, "exist");
    private final static QName ATTRIB_ELEMENT = new QName("attribute", EXIST_NS, "exist");
    private final static QName SOURCE_ATTRIB = new QName("source", EXIST_NS, "exist");
    private final static QName ID_ATTRIB = new QName("id", EXIST_NS, "exist");
    
    private int showId = EXIST_ID_ELEMENT;

    public NativeSerializer(DBBroker broker, Configuration config) {
        super(broker, config);
        String showIdParam = (String) config
                .getProperty("serialization.add-exist-id");
        if (showIdParam != null) {
            if (showIdParam.equals("element"))
                showId = EXIST_ID_ELEMENT;
            else if (showIdParam.equals("all"))
                showId = EXIST_ID_ALL;
            else
                showId = EXIST_ID_NONE;
        }
    }

    protected void serializeToReceiver(NodeProxy p, boolean generateDocEvent)
    throws SAXException {
    	if(Type.subTypeOf(p.getType(), Type.DOCUMENT) || p.gid < 0) {
    			serializeToReceiver(p.getDocument(), generateDocEvent);
    			return;
    	}
    	setDocument(p.getDocument());
    	if (generateDocEvent) receiver.startDocument();
        Iterator domIter = broker.getNodeIterator(p);
        serializeToReceiver(null, domIter, p.getDocument(), p.gid, true, p.getMatches(), new TreeSet());
        if (generateDocEvent) receiver.endDocument();
    }
    
    protected void serializeToReceiver(DocumentImpl doc, boolean generateDocEvent)
    throws SAXException {
    	long start = System.currentTimeMillis();
    	setDocument(doc);
    	NodeList children = doc.getChildNodes();
    	if (generateDocEvent) receiver.startDocument();
		if (doc.getDoctype()!=null){
			if (getProperty(EXistOutputKeys.OUTPUT_DOCTYPE, "no").equals("yes")) {
				final NodeImpl n = (NodeImpl) doc.getDoctype();
				serializeToReceiver(n, null, (DocumentImpl) n.getOwnerDocument(), n
						.getGID(), true, null, new TreeSet());
			}
		}
    	// iterate through children
    	for (int i = 0; i < children.getLength(); i++) {
    		final NodeImpl n = (NodeImpl) children.item(i);
    		final NodeProxy p = new NodeProxy((DocumentImpl) n
    				.getOwnerDocument(), n.getGID(), n.getInternalAddress());
    		Iterator domIter = broker.getNodeIterator(p);
    		domIter.next();
    		serializeToReceiver(n, domIter, (DocumentImpl) n.getOwnerDocument(), n
    				.getGID(), true, p.getMatches(), new TreeSet());
    	}
    	DocumentImpl documentImpl = (DocumentImpl) doc;
		LOG.debug("serializing document " + documentImpl.getDocId()
				+ " (" + documentImpl.getName() + ")"
    			+ " to SAX took " + (System.currentTimeMillis() - start));
    	if (generateDocEvent) receiver.endDocument();
    }
    
    protected void serializeToReceiver(NodeImpl node, Iterator iter,
            DocumentImpl doc, long gid, boolean first, Match match, Set namespaces) 
    throws SAXException {
        if (node == null) node = (NodeImpl) iter.next();
        if (node == null) return;
        // char ch[];
        String cdata;
        switch (node.getNodeType()) {
        case Node.ELEMENT_NODE:
        	String defaultNS = null;
	        if (((ElementImpl) node).declaresNamespacePrefixes()) {
	        	// declare namespaces used by this element
	        	String prefix, uri;
	        	for (Iterator i = ((ElementImpl) node).getPrefixes(); i.hasNext();) {
	        		prefix = (String) i.next();
	        		if (prefix.length() == 0) {
	        			defaultNS = ((ElementImpl) node).getNamespaceForPrefix(prefix);
	        			receiver.startPrefixMapping("", defaultNS);
	        			namespaces.add(defaultNS);
	        		} else {
	        			uri = ((ElementImpl) node).getNamespaceForPrefix(prefix);
	        			receiver.startPrefixMapping(prefix, uri);
	        			namespaces.add(uri);
	        		}
	        	}
	        }
	        String ns = defaultNS == null ? node.getNamespaceURI() : defaultNS;
	        if (ns.length() > 0 && (!namespaces.contains(ns)))
	        	receiver.startPrefixMapping(node.getPrefix(), ns);
        	AttrList attribs = new AttrList();
        	if ((first && showId == EXIST_ID_ELEMENT) || showId == EXIST_ID_ALL) {
                attribs.addAttribute(ID_ATTRIB, Long.toString(gid));
            }
            if (first && showId > 0) {
            	// String src = doc.getCollection().getName() + '/' + doc.getFileName();
                attribs.addAttribute(SOURCE_ATTRIB, doc.getFileName());
            }
            int children = node.getChildCount();
            int count = 0;
            // int childLen;
            NodeImpl child = null;
            if (children > 0) gid = XMLUtil.getFirstChildId(doc, gid);
            while (count < children) {
                child = (NodeImpl) iter.next();
                if (child.getNodeType() == Node.ATTRIBUTE_NODE) {
                    if ((getHighlightingMode() & TAG_ATTRIBUTE_MATCHES) > 0)
                        cdata = processAttribute(((AttrImpl) child).getValue(),
                                gid, match);
                    else
                        cdata = ((AttrImpl) child).getValue();
                    attribs.addAttribute(child.getQName(), cdata);
                    count++;
                    gid++;
                    child.release();
                } else
                    break;
            }
            receiver.startElement(node.getQName(), attribs);
            while (count < children) {
                serializeToReceiver(child, iter, doc, gid++, false, match, namespaces);
                if (++count < children) {
                    child = (NodeImpl) iter.next();
                } else
                    break;
            }
            receiver.endElement(node.getQName());
            if (((ElementImpl) node).declaresNamespacePrefixes()) {
                String prefix;
                for (Iterator i = ((ElementImpl) node).getPrefixes(); i
                        .hasNext();) {
                    prefix = (String) i.next();
                    receiver.endPrefixMapping(prefix);
                }
            }
            if (ns.length() > 0 && (!namespaces.contains(ns)))
                    receiver.endPrefixMapping(node.getPrefix());
            node.release();
            break;
        case Node.TEXT_NODE:
        	if (first && createContainerElements) {
                AttrList tattribs = new AttrList();
                if (showId > 0) {
                    tattribs.addAttribute(ID_ATTRIB, Long.toString(gid));
                    tattribs.addAttribute(SOURCE_ATTRIB, doc.getFileName());
                }
                receiver.startElement(TEXT_ELEMENT, tattribs);
            }
            if ((getHighlightingMode() & TAG_ELEMENT_MATCHES) == TAG_ELEMENT_MATCHES)
                textToReceiver((TextImpl) node, gid, match);
            else {
                receiver.characters(((TextImpl) node).getXMLString());
            }
            if (first && createContainerElements)
                receiver.endElement(TEXT_ELEMENT);
            node.release();
            break;
        case Node.ATTRIBUTE_NODE:
            if ((getHighlightingMode() & TAG_ATTRIBUTE_MATCHES) == TAG_ATTRIBUTE_MATCHES)
                cdata = processAttribute(((AttrImpl) node).getValue(), gid,
                        match);
            else
                cdata = ((AttrImpl) node).getValue();
        	if(first && createContainerElements) {
        		AttrList tattribs = new AttrList();
                if (showId > 0) {
                    tattribs.addAttribute(ID_ATTRIB, Long.toString(gid));
                    tattribs.addAttribute(SOURCE_ATTRIB, doc.getFileName());
                }
                tattribs.addAttribute(((AttrImpl)node).getQName(), cdata);
                receiver.startElement(ATTRIB_ELEMENT, tattribs);
                receiver.endElement(ATTRIB_ELEMENT);
        	} else
        		receiver.attribute(node.getQName(), cdata);
            node.release();
            break;
		case Node.DOCUMENT_TYPE_NODE:
			String systemId = ((DocumentTypeImpl) node).getSystemId();
			String publicId =  ((DocumentTypeImpl) node).getPublicId();
			String name = ((DocumentTypeImpl) node).getName();
			receiver.documentType(name, publicId, systemId);
			break;
        case Node.PROCESSING_INSTRUCTION_NODE:
            receiver.processingInstruction(
                    ((ProcessingInstructionImpl) node).getTarget(),
                    ((ProcessingInstructionImpl) node).getData());
            node.release();
            break;
        case Node.COMMENT_NODE:
            String comment = ((CommentImpl) node).getData();
            char data[] = new char[comment.length()];
            comment.getChars(0, data.length, data, 0);
            receiver.comment(data, 0, data.length);
            node.release();
            break;
        }
    }

    private final String processAttribute(String data, long gid, Match match) {
        if (match == null) return data;
        // prepare a regular expression to mark match-terms
        StringBuffer expr = null;
        Match next = match;
        while (next != null) {
            if (next.getNodeId() == gid) {
                if (expr == null) {
                    expr = new StringBuffer();
                    expr.append("\\b(");
                }
                if (expr.length() > 5) expr.append('|');
                expr.append("");
            }
            next = next.getNextMatch();
        }
        if (expr != null) {
            expr.append(")\\b");
            Pattern pattern = Pattern.compile(expr.toString(), Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
            Matcher matcher = pattern.matcher(data);
            return matcher.replaceAll("||$1||");
        }
        return data;
    }

    private final void textToReceiver(TextImpl text, long gid, Match match) throws SAXException {
        if (match == null) {
            receiver.characters(text.getXMLString());
        } else {
            List offsets = null;
            Match next = match;
            while (next != null) {
                if (next.getNodeId() == gid) {
                    if (offsets == null)
                        offsets = new ArrayList();
                    int freq = next.getFrequency();
                    for (int i = 0; i < freq; i++) {
                        offsets.add(next.getOffset(i));
                    }
                }
                next = next.getNextMatch();
            }
            
            if (offsets != null) {
                FastQSort.sort(offsets, 0, offsets.size() - 1);
                
                XMLString str = text.getXMLString();
                Match.Offset offset;
                int pos = 0;
                for (int i = 0; i < offsets.size(); i++) {
                    offset = (Match.Offset) offsets.get(i);
                    if (offset.getOffset() > pos) {
                        receiver.characters(str.substring(pos, offset.getOffset() - pos));
                    }
                    receiver.startElement(MATCH_ELEMENT, null);
                    receiver.characters(str.substring(offset.getOffset(), offset.getLength()));
                    receiver.endElement(MATCH_ELEMENT);
                    pos = offset.getOffset() + offset.getLength();
                }
                if (pos < str.length())
                    receiver.characters(str.substring(pos, str.length() - pos));
            } else {
                receiver.characters(text.getXMLString());
            }
        }
    }
    
    private final void textToReceiver(String data, Receiver receiver)
            throws SAXException {
        int p0 = 0, p1;
        boolean inTerm = false;
        while (p0 < data.length()) {
            p1 = data.indexOf("||", p0);
            if (p1 < 0) {
                receiver.characters(data.substring(p0));
                break;
            }
            if (inTerm) {
                receiver.startElement(MATCH_ELEMENT, null);
                receiver.characters(data.substring(p0, p1));
                receiver.endElement(MATCH_ELEMENT);
                inTerm = false;
            } else {
                inTerm = true;
                receiver.characters(data.substring(p0, p1));
            }
            p0 = p1 + 2;
        }
    }
}
