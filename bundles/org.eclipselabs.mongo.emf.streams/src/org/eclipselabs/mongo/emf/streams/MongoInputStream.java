/*******************************************************************************
 * Copyright (c) 2011 Bryan Hunt & Ed Merks.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Bryan Hunt & Ed Merks - initial API and implementation
 *******************************************************************************/

package org.eclipselabs.mongo.emf.streams;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

import org.eclipse.emf.common.util.EList;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.URIConverter;
import org.eclipse.emf.ecore.util.InternalEList;
import org.eclipse.emf.ecore.xmi.XMLResource;
import org.eclipselabs.mongo.emf.EObjectBuilder;
import org.eclipselabs.mongo.emf.IConverterService;
import org.eclipselabs.mongo.emf.IEObjectBuilderFactory;
import org.eclipselabs.mongo.emf.IQueryEngine;
import org.eclipselabs.mongo.emf.MongoQuery;
import org.eclipselabs.mongo.emf.MongoURIHandlerImpl;
import org.eclipselabs.mongo.emf.ext.ECollection;
import org.eclipselabs.mongo.emf.ext.ExtFactory;
import org.eclipselabs.mongo.emf.model.ModelFactory;
import org.eclipselabs.mongo.emf.model.MongoCursor;

import com.mongodb.BasicDBObject;
import com.mongodb.DBCollection;
import com.mongodb.DBCursor;
import com.mongodb.DBObject;
import com.mongodb.util.JSON;

/**
 * @author bhunt
 * 
 */
public class MongoInputStream extends InputStream implements URIConverter.Loadable
{
	public MongoInputStream(IConverterService converterService, IEObjectBuilderFactory builderFactory, IQueryEngine queryEngine, DBCollection collection, URI uri, Map<?, ?> options, Map<Object, Object> response) throws IOException
	{
		if (converterService == null)
			throw new NullPointerException("The converter service must not be null");

		if (collection == null)
			throw new NullPointerException("The database collection must not be null");

		this.converterService = converterService;
		this.builderFactory = builderFactory;
		this.queryEngine = queryEngine;
		this.collection = collection;
		this.uri = uri;
		this.options = options;
		this.response = response;
	}

	@Override
	public void loadResource(Resource resource) throws IOException
	{
		// We need to set up the XMLResource.URIHandler so that proxy URIs are handled properly.

		XMLResource.URIHandler uriHandler = (XMLResource.URIHandler) options.get(XMLResource.OPTION_URI_HANDLER);

		if (uriHandler == null)
			uriHandler = new org.eclipse.emf.ecore.xmi.impl.URIHandlerImpl();

		if (resource.getURI().hasQuery())
			uriHandler.setBaseURI(resource.getURI().trimSegments(1).appendSegment("-1"));
		else
			uriHandler.setBaseURI(resource.getURI());

		boolean includeAttributesForProxyReferences = Boolean.TRUE.equals(options.get(MongoURIHandlerImpl.OPTION_PROXY_ATTRIBUTES));
		EObjectBuilder builder = builderFactory.createObjectBuilder(converterService, uriHandler, includeAttributesForProxyReferences, eClassCache);

		// If the URI contains a query string, use it to locate a collection of objects from
		// MongoDB, otherwise simply get the object from MongoDB using the id.

		EList<EObject> contents = resource.getContents();

		if (uri.query() != null)
		{
			MongoQuery mongoQuery = null;

			if (uri.query().startsWith("mongo:"))
			{
				String decodedQuery = URI.decode(uri.query());
				int sortIndex = decodedQuery.indexOf("sort:");

				DBObject objectFilter = (DBObject) JSON.parse(decodedQuery.substring(6, sortIndex));
				DBObject sortFilter = null;

				if (sortIndex > 0)
					sortFilter = (DBObject) JSON.parse(decodedQuery.substring(sortIndex + 5));

				mongoQuery = new MongoQuery(objectFilter, null, sortFilter);
			}
			else
			{
				if (queryEngine == null)
					throw new IOException("The query engine was not found");

				mongoQuery = queryEngine.buildDBObjectQuery(uri);
			}

			DBCursor resultCursor = null;

			if (mongoQuery.getFieldFilter() == null)
				resultCursor = collection.find(mongoQuery.getObjectFilter());
			else
				resultCursor = collection.find(mongoQuery.getObjectFilter(), mongoQuery.getFieldFilter());

			if (mongoQuery.getSortFilter() != null)
				resultCursor = resultCursor.sort(mongoQuery.getSortFilter());

			boolean createCursor = Boolean.TRUE.equals(options.get(MongoURIHandlerImpl.OPTION_QUERY_CURSOR));

			if (createCursor)
			{
				MongoCursor cursor = ModelFactory.eINSTANCE.createMongoCursor();
				cursor.setDbCollection(collection);
				cursor.setDbCursor(resultCursor);
				cursor.setObjectBuilder(builder);
				contents.add(cursor);
			}
			else
			{
				ECollection eCollection = ExtFactory.eINSTANCE.createECollection();
				InternalEList<EObject> values = (InternalEList<EObject>) eCollection.getValues();

				for (DBObject dbObject : resultCursor)
					values.addUnique(builder.buildEObject(collection, dbObject, resource, true));

				contents.add(eCollection);
			}
		}
		else
		{
			DBObject dbObject = collection.findOne(new BasicDBObject(MongoURIHandlerImpl.ID_KEY, MongoURIHandlerImpl.getID(uri)));

			if (dbObject != null)
			{
				EObject eObject = builder.buildEObject(collection, dbObject, resource, false);

				if (eObject != null)
					contents.add(eObject);

				response.put(URIConverter.RESPONSE_TIME_STAMP_PROPERTY, dbObject.get(MongoURIHandlerImpl.TIME_STAMP_KEY));
			}
		}
	}

	@Override
	public int read() throws IOException
	{
		// InputStream requires that we implement this function. It will never be called
		// since this implementation implements URIConverter.Loadable. The loadResource()
		// function will be called instead.

		return 0;
	}

	private URI uri;
	private Map<?, ?> options;
	private Map<Object, Object> response;
	private IConverterService converterService;
	private IQueryEngine queryEngine;
	private DBCollection collection;
	private Map<String, EClass> eClassCache = new HashMap<String, EClass>();
	private IEObjectBuilderFactory builderFactory;
}
