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

package org.eclipselabs.mongo.emf.ext;

import org.eclipse.emf.ecore.resource.ResourceSet;

/**
 * The ResourceSet from this service is not thread safe. This service
 * is intended to be used on the UI thread.
 * 
 * @author bhunt
 * 
 */
public interface IResourceCache
{
	public ResourceSet getResourceSet();
}