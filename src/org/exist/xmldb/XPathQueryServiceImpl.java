/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2001-2015 The eXist Project
 * http://exist-db.org
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 */
package org.exist.xmldb;

import org.xmldb.api.base.ResourceSet;
import org.xmldb.api.base.XMLDBException;
import org.xmldb.api.modules.XMLResource;
import org.xmldb.api.modules.XPathQueryService;

/**
 * Extends {@link org.xmldb.api.modules.XPathQueryService} by additional
 * methods specific to eXist.
 *
 * @author Wolfgang <wolfgang@exist-db.org>
 */
public interface XPathQueryServiceImpl extends XPathQueryService {

    /**
     * Process an XPath query based on the result of a previous query.
     * The XMLResource contains the result received from a previous
     * query.
     *
     * @param res   an XMLResource as obtained from a previous query.
     * @param query the XPath query
     */
    ResourceSet query(XMLResource res, String query)
            throws XMLDBException;

    /**
     * Process an XPath query based on the result of a previous query and sort the
     * results using the second XPath expression. The XMLResource contains
     * the result received from a previous query.
     *
     * @param res      an XMLResource as obtained from a previous query
     * @param query    the XPath query
     * @param sortExpr another XPath expression, which is executed relative to
     *                 the results of the primary expression. The result of applying sortExpr is converted
     *                 to a string value, which is then used to sort the results.
     * @throws XMLDBException
     */
    ResourceSet query(XMLResource res, String query, String sortExpr)
            throws XMLDBException;

    /**
     * Process an XPath query and sort the results by applying a second XPath expression
     * to each of the search results. The result of applying the sort expression is converted
     * into a string, which is then used to sort the set of results.
     *
     * @param query    the XPath query
     * @param sortExpr another XPath expression, which is executed relative to the
     *                 results of the primary expression.
     * @throws XMLDBException
     */
    ResourceSet query(String query, String sortExpr)
            throws XMLDBException;

    /**
     * Executes a query which is already stored in the database
     *
     * @param uri The URI of the query in the database
     * @throws XMLDBException
     */
    ResourceSet executeStoredQuery(String uri) throws XMLDBException;

    /**
     * Declare an external XPath variable and assign a value to it.
     * <p>
     * A variable can be referenced inside an XPath expression as
     * <b>$variable</b>. For example, if you declare a variable with
     * <p>
     * <pre>
     * 	declareVariable("name", "HAMLET");
     * </pre>
     * <p>
     * you may use the variable in an XPath expression as follows:
     * <p>
     * <pre>
     * 	//SPEECH[SPEAKER=$name]
     * </pre>
     * <p>
     * Any Java object may be passed as initial value. The query engine will try
     * to map this into a corresponding XPath value. You may also pass an
     * XMLResource as obtained from another XPath expression. This will be
     * converted into a node.
     *
     * @param qname        a valid QName by which the variable is identified. Any
     *                     prefix should have been mapped to a namespace, i.e. if a variable is called
     *                     <b>x:name</b>, there should be a prefix/namespace mapping for the prefix
     *                     x
     * @param initialValue the initial value, which is assigned to the variable
     * @throws XMLDBException
     */
    void declareVariable(String qname, Object initialValue) throws XMLDBException;

    /**
     * Clears any previously declared variables
     */
    void clearVariables() throws XMLDBException;

    /**
     * Execute all following queries in a protected environment. Acquire a write lock
     * on all resources in the current collection (i.e. the one from which this service
     * was obtained) before executing the query. If a query spans multiple collections,
     * call beginProtected on the outer collection which contains all the other
     * collections.
     * <p>
     * It is thus guaranteed that documents referenced by the
     * query or the result set are not modified by other threads
     * until {@link #endProtected} is called.
     */
    void beginProtected() throws XMLDBException;

    /**
     * Close the protected environment. All locks held
     * by the current thread are released. The query result set
     * is no longer guaranteed to be stable.
     * <p>
     * Note: if beginProtected was used, you have to make sure
     * endProtected is called in ALL cases. Otherwise some resource
     * locks may not be released properly.
     */
    void endProtected();
}