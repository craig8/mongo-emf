/*******************************************************************************
 * Copyright (c) 2012 Bryan Hunt.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Bryan Hunt - initial API and implementation
 *******************************************************************************/

package org.eclipselabs.mongo.internal.emf;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.eclipse.emf.common.util.EList;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EAttribute;
import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EDataType;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EReference;
import org.eclipse.emf.ecore.EStructuralFeature;
import org.eclipse.emf.ecore.InternalEObject;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.util.EcoreUtil;
import org.eclipse.emf.ecore.util.FeatureMap;
import org.eclipse.emf.ecore.util.FeatureMapUtil;
import org.eclipse.emf.ecore.util.InternalEList;
import org.eclipse.emf.ecore.xmi.XMLResource;
import org.eclipselabs.mongo.emf.IConverterService;
import org.eclipselabs.mongo.emf.MongoDBURIHandlerImpl;

import com.mongodb.BasicDBObject;
import com.mongodb.DBObject;

/**
 * @author bhunt
 * 
 */
public class DBObjectBuilder
{
	public DBObjectBuilder(IConverterService converterService, XMLResource.URIHandler uriHandler, boolean serializeDefaultAttributeValues)
	{
		this.converterService = converterService;
		this.uriHandler = uriHandler;
		this.serializeDefaultAttributeValues = serializeDefaultAttributeValues;
	}

	public DBObject buildDBObject(EObject eObject) throws IOException
	{
		// Build a MongoDB object from the EMF object.

		BasicDBObject dbObject = new BasicDBObject();
		EClass eClass = eObject.eClass();

		// We have to add the URI of the class to the object so that we can
		// reconstruct the EMF object when we read it back out of MongoDB.

		dbObject.put(MongoDBURIHandlerImpl.ECLASS_KEY, EcoreUtil.getURI(eClass).toString());

		// Save the XML extrinsic id if necessary

		buildExtrensicID(eObject, dbObject, eObject.eResource());

		// All attributes are mapped as key / value pairs with the key being the attribute name.

		for (EAttribute attribute : eClass.getEAllAttributes())
		{
			if (!attribute.isTransient() && (eObject.eIsSet(attribute) || (!attribute.isUnsettable() && serializeDefaultAttributeValues)))
				buildAttribute(eObject, dbObject, attribute);
		}

		// All references are mapped as key / value pairs with the key being the reference name.

		for (EReference reference : eClass.getEAllReferences())
		{
			if (!reference.isTransient() && eObject.eIsSet(reference))
				buildReference(eObject, dbObject, reference);
		}

		return dbObject;
	}

	/**
	 * @param eObject
	 * @param dbObject
	 * @param attribute
	 * @throws IOException
	 */
	protected void buildAttribute(EObject eObject, BasicDBObject dbObject, EAttribute attribute) throws IOException
	{
		Object value = eObject.eGet(attribute);

		if (FeatureMapUtil.isFeatureMap(attribute))
		{
			FeatureMap.Internal featureMap = (FeatureMap.Internal) value;
			Iterator<FeatureMap.Entry> iterator = featureMap.basicIterator();
			ArrayList<DBObject> dbFeatureMap = new ArrayList<DBObject>();

			while (iterator.hasNext())
			{
				DBObject dbEntry = new BasicDBObject();
				FeatureMap.Entry entry = iterator.next();
				EStructuralFeature feature = entry.getEStructuralFeature();
				dbEntry.put("key", EcoreUtil.getURI(feature).toString());

				if (feature instanceof EAttribute)
					dbEntry.put("value", buildAttributeValue((EAttribute) feature, entry.getValue()));
				else
					dbEntry.put("value", buildReference((EReference) feature, (EObject) entry.getValue()));

				dbFeatureMap.add(dbEntry);
			}

			value = dbFeatureMap;
		}
		else if (attribute.isMany())
		{
			EDataType eDataType = attribute.getEAttributeType();

			if (!MongoDBURIHandlerImpl.isNativeType(eDataType))
			{
				EList<?> rawValues = (EList<?>) value;
				ArrayList<Object> values = new ArrayList<Object>(rawValues.size());

				for (Object rawValue : rawValues)
					values.add(converterService.convertEMFValueToMongoDBValue(eDataType, rawValue));

				value = values;
			}
		}
		else
			value = buildAttributeValue(attribute, value);

		dbObject.put(attribute.getName(), value);
	}

	/**
	 * @param eObject
	 * @param dbObject
	 * @param resource
	 */
	protected void buildExtrensicID(EObject eObject, BasicDBObject dbObject, Resource resource)
	{
		if (resource instanceof XMLResource)
		{
			String id = ((XMLResource) resource).getID(eObject);

			if (id != null)
				dbObject.put(MongoDBURIHandlerImpl.EXTRINSIC_ID_KEY, id);
		}
	}

	/**
	 * @param eObject
	 * @param dbObject
	 * @param reference
	 * @throws IOException
	 */
	protected void buildReference(EObject eObject, BasicDBObject dbObject, EReference reference) throws IOException
	{
		Object value = eObject.eGet(reference, false);

		if (reference.isMany())
		{
			// One to many reference

			@SuppressWarnings("unchecked")
			List<EObject> targetObjects = ((InternalEList<EObject>) value).basicList();
			ArrayList<Object> dbReferences = new ArrayList<Object>(targetObjects.size());

			for (EObject targetObject : targetObjects)
				dbReferences.add(buildReference(reference, targetObject));

			value = dbReferences;
		}
		else if (value != null)
		{
			// One to one reference

			EObject targetObject = (EObject) value;
			value = buildReference(reference, targetObject);
		}

		dbObject.put(reference.getName(), value);
	}

	protected Object buildReference(EReference eReference, EObject targetObject) throws IOException
	{
		InternalEObject internalEObject = (InternalEObject) targetObject;
		URI eProxyURI = internalEObject.eProxyURI();

		if (eProxyURI != null)
		{
			BasicDBObject dbObject = new BasicDBObject(2);
			dbObject.put(MongoDBURIHandlerImpl.PROXY_KEY, uriHandler.deresolve(eProxyURI).toString());
			dbObject.put(MongoDBURIHandlerImpl.ECLASS_KEY, EcoreUtil.getURI(targetObject.eClass()).toString());
			return dbObject;
		}
		else if (!eReference.isContainment() || (eReference.isResolveProxies() && internalEObject.eDirectResource() != null))
		{
			// Cross-document containment, or non-containment reference - build a proxy

			BasicDBObject dbObject = new BasicDBObject(2);
			dbObject.put(MongoDBURIHandlerImpl.PROXY_KEY, uriHandler.deresolve(EcoreUtil.getURI(targetObject)).toString());
			dbObject.put(MongoDBURIHandlerImpl.ECLASS_KEY, EcoreUtil.getURI(targetObject.eClass()).toString());
			return dbObject;
		}
		else
		{
			// Non cross-document containment reference - build a MongoDB embedded object

			return buildDBObject(targetObject);
		}
	}

	protected Object buildAttributeValue(EAttribute attribute, Object rawValue)
	{
		EDataType eDataType = attribute.getEAttributeType();

		if (!MongoDBURIHandlerImpl.isNativeType(eDataType))
			return converterService.convertEMFValueToMongoDBValue(eDataType, rawValue);

		return rawValue;
	}

	private IConverterService converterService;
	private XMLResource.URIHandler uriHandler;
	private boolean serializeDefaultAttributeValues;
}