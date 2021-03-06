/*******************************************************************************
 * Copyright (c) 2011 Bryan Hunt.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Bryan Hunt - initial API and implementation
 *******************************************************************************/

package org.eclipselabs.mongo.emf.junit.support;

import java.io.IOException;
import java.util.Dictionary;
import java.util.Hashtable;

import org.eclipselabs.mongo.IMongoProvider;
import org.osgi.service.cm.Configuration;
import org.osgi.service.cm.ConfigurationAdmin;

/**
 * This class was introduced because the replica test needs to set up a replica set
 * instead of a single database.
 * 
 * @author bhunt
 */
public class MongoConfigurator extends BaseConfigurator
{
	@Override
	protected void configureMongoProvider(ConfigurationAdmin configurationAdmin) throws IOException
	{
		Configuration config = configurationAdmin.createFactoryConfiguration("org.eclipselabs.mongo.provider", null);

		Dictionary<String, Object> properties = new Hashtable<String, Object>();

		properties.put(IMongoProvider.PROP_URI, "mongodb://localhost/");
		properties.put("type", "mongo");
		config.update(properties);
	}
}
