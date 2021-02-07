package test;
/* FeatureIDE - A Framework for Feature-Oriented Software Development
 * Copyright (C) 2005-2019  FeatureIDE team, University of Magdeburg, Germany
 *
 * This file is part of FeatureIDE.
 *
 * FeatureIDE is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * FeatureIDE is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with FeatureIDE.  If not, see <http://www.gnu.org/licenses/>.
 *
 * See http://featureide.cs.ovgu.de/ for further information.
 */

import java.io.*;
import java.util.*;

import javax.xml.parsers.*;

import org.spldev.formula.clause.*;
import org.spldev.formula.expression.*;
import org.spldev.formula.expression.atomic.literal.*;
import org.spldev.formula.expression.compound.*;
import org.spldev.util.*;
import org.spldev.util.io.format.*;
import org.w3c.dom.*;
import org.xml.sax.*;

public class XmlCNFFormat implements Format<CNF>, XMLFeatureModelTags {

	private ArrayList<Formula> constraints = new ArrayList<>();

	public XmlCNFFormat() {
	}

	/**
	 * Returns a list of elements within the given node list.
	 *
	 * @param nodeList the node list.
	 * @return The child nodes from type Element of the given NodeList.
	 */
	private static final List<Element> getElements(NodeList nodeList) {
		final ArrayList<Element> elements = new ArrayList<>(nodeList.getLength());
		for (int temp = 0; temp < nodeList.getLength(); temp++) {
			final org.w3c.dom.Node nNode = nodeList.item(temp);
			if (nNode.getNodeType() == org.w3c.dom.Node.ELEMENT_NODE) {
				final Element eElement = (Element) nNode;
				elements.add(eElement);
			}
		}
		return elements;
	}

	private List<Element> getElement(final Element element, final String nodeName) {
		return getElements(element.getElementsByTagName(nodeName));
	}

	private List<Element> getElement(final Document document, final String nodeName) {
		return getElements(document.getElementsByTagName(nodeName));
	}

	@Override
	public String getFileExtension() {
		return "xml";
	}

	@Override
	public Result<CNF> parse(CharSequence source) {
		try {
			final Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
			SAXParserFactory.newInstance().newSAXParser().parse(new InputSource(new StringReader(source.toString())),
				new PositionalXMLHandler(doc));
			doc.getDocumentElement().normalize();
			Formula readDocument = readDocument(doc);
			return Result.of(Clauses.convertToCNF(readDocument));
		} catch (final Exception e) {
			return Result.empty(new Problem(e));
		}
	}

	@Override
	public boolean supportsParse() {
		return true;
	}

	private Formula readDocument(Document doc) {
		for (final Element e : getElement(doc, FEATURE_MODEL)) {
			parseStruct(getElement(e, STRUCT));
			parseConstraints(getElement(e, CONSTRAINTS));
		}
		return new And(constraints);
	}

	private void parseConstraints(List<Element> elements) {
		for (final Element e : elements) {
			for (final Element child : getElements(e.getChildNodes())) {
				final String nodeName = child.getNodeName();
				if (nodeName.equals(RULE)) {
					constraints.add(parseConstraintNode(child.getChildNodes()).get(0));
				}
			}
		}
	}

	private List<Formula> parseConstraintNode(NodeList nodeList) {
		final List<Formula> nodes = new ArrayList<>();
		List<Formula> children;
		for (final Element e : getElements(nodeList)) {
			final String nodeName = e.getNodeName();
			switch (nodeName) {
			case DISJ:
				nodes.add(new Or(parseConstraintNode(e.getChildNodes())));
				break;
			case CONJ:
				nodes.add(new And(parseConstraintNode(e.getChildNodes())));
				break;
			case EQ:
				children = parseConstraintNode(e.getChildNodes());
				nodes.add(new Biimplies(children.get(0), children.get(1)));
				break;
			case IMP:
				children = parseConstraintNode(e.getChildNodes());
				nodes.add(new Implies(children.get(0), children.get(1)));
				break;
			case NOT:
				nodes.add(new Not((parseConstraintNode(e.getChildNodes())).get(0)));
				break;
			case ATMOST1:
				nodes.add(new AtMost(parseConstraintNode(e.getChildNodes()), 1));
				break;
			case VAR:
				nodes.add(new LiteralVariable(e.getTextContent()));
				break;
			default:
				break;
			}
		}
		return nodes;
	}

	private ArrayList<Formula> parseFeatures(NodeList nodeList, Formula parent) {
		final ArrayList<Formula> children = new ArrayList<>();
		for (final Element e : getElements(nodeList)) {
			final String nodeName = e.getNodeName();
			switch (nodeName) {
			case AND:
			case OR:
			case ALT:
			case FEATURE:
				children.add(parseFeature(parent, e, nodeName));
				break;
			default:
				break;
			}
		}
		return children;
	}

	private LiteralVariable parseFeature(Formula parent, final Element e, final String nodeName) {
		boolean mandatory = false;
		String name = "";
		if (e.hasAttributes()) {
			final NamedNodeMap nodeMap = e.getAttributes();
			for (int i = 0; i < nodeMap.getLength(); i++) {
				final org.w3c.dom.Node node = nodeMap.item(i);
				final String attributeName = node.getNodeName();
				final String attributeValue = node.getNodeValue();
				if (attributeName.equals(MANDATORY)) {
					mandatory = attributeValue.equals(TRUE);
				} else if (attributeName.equals(NAME)) {
					name = attributeValue;
				}

			}
		}

		final LiteralVariable f = new LiteralVariable(name);

		if (parent == null) {
			constraints.add(f.cloneNode());
		} else {
			constraints.add(new Implies(f.cloneNode(), parent));
			if (mandatory) {
				constraints.add(new Implies(parent, f.cloneNode()));
			}
		}

		if (e.hasChildNodes()) {
			final ArrayList<Formula> parseFeatures = parseFeatures(e.getChildNodes(), f);
			switch (nodeName) {
			case AND:
				break;
			case OR:
				constraints.add(new Implies(f.cloneNode(), new Or(parseFeatures)));
				break;
			case ALT:
				constraints.add(new Implies(f.cloneNode(), new Choose(parseFeatures, 1)));
				break;
			default:
				break;
			}
		}

		return f;
	}

	private void parseStruct(List<Element> elements) {
		for (final Element e : elements) {
			parseFeatures(e.getChildNodes(), null);
		}
	}

	@Override
	public XmlCNFFormat getInstance() {
		return new XmlCNFFormat();
	}

	@Override
	public String getId() {
		return "FeatureIDEXMLFormat";
	}

	@Override
	public String getName() {
		return "FeatureIDE";
	}

}
