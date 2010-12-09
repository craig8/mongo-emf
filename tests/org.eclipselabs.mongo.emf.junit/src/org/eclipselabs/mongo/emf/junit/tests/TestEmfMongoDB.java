/*******************************************************************************
 * Copyright (c) 2010 Bryan Hunt.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Bryan Hunt - initial API and implementation
 *******************************************************************************/

package org.eclipselabs.mongo.emf.junit.tests;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.sameInstance;
import static org.junit.Assert.assertThat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.bson.types.ObjectId;
import org.eclipse.emf.common.util.EList;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.resource.URIHandler;
import org.eclipse.emf.ecore.resource.impl.BinaryResourceImpl;
import org.eclipse.emf.ecore.resource.impl.ResourceSetImpl;
import org.eclipse.emf.ecore.util.EcoreUtil;
import org.eclipse.emf.ecore.xmi.XMIResource;
import org.eclipse.emf.ecore.xmi.XMLResource;
import org.eclipse.emf.ecore.xmi.impl.XMIResourceFactoryImpl;
import org.eclipse.emf.ecore.xmi.impl.XMLResourceFactoryImpl;
import org.eclipselabs.mongo.IMongoDB;
import org.eclipselabs.mongo.emf.MongoDBURIHandlerImpl;
import org.eclipselabs.mongo.emf.junit.internal.Activator;
import org.eclipselabs.mongo.emf.junit.model.Book;
import org.eclipselabs.mongo.emf.junit.model.Library;
import org.eclipselabs.mongo.emf.junit.model.Location;
import org.eclipselabs.mongo.emf.junit.model.ModelFactory;
import org.eclipselabs.mongo.emf.junit.model.ModelPackage;
import org.eclipselabs.mongo.emf.junit.model.Person;
import org.eclipselabs.mongo.internal.emf.MongoDBResourceFactoryImpl;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.mongodb.BasicDBObject;
import com.mongodb.DB;
import com.mongodb.DBCollection;
import com.mongodb.DBObject;
import com.mongodb.DBRef;
import com.mongodb.Mongo;
import com.mongodb.MongoException;
import com.mongodb.MongoURI;

/**
 * @author bhunt
 * 
 */
public class TestEmfMongoDB
{
	@Rule
	public TemporaryFolder temporaryFolder = new TemporaryFolder();

	@Before
	public void setUp() throws UnknownHostException, MongoException
	{
		IMongoDB mongoService = Activator.getInstance().getMongoDB();
		assertThat(mongoService, is(notNullValue()));

		mongo = mongoService.getMongo(new MongoURI("mongodb://localhost"));
		assertThat(mongo, is(notNullValue()));

		db = mongo.getDB("test");
		assertThat(db, is(notNullValue()));

		personCollection = db.getCollection(ModelPackage.Literals.PERSON.getName());
		libraryCollection = db.getCollection(ModelPackage.Literals.LIBRARY.getName());
		locationCollection = db.getCollection(ModelPackage.Literals.LOCATION.getName());

		personCollection.ensureIndex(ModelPackage.Literals.PERSON__NAME.getName());
	}

	@After
	public void tearDown()
	{
		if (libraryCollection != null)
			libraryCollection.drop();

		if (personCollection != null)
			personCollection.drop();

		if (locationCollection != null)
			locationCollection.drop();
	}

	@Test
	public void testSaveAuthor() throws IOException
	{
		Person author = ModelFactory.eINSTANCE.createPerson();
		author.setName("Stephen King");

		ResourceSet resourceSet = new ResourceSetImpl();
		EList<URIHandler> uriHandlers = resourceSet.getURIConverter().getURIHandlers();
		uriHandlers.add(0, new MongoDBURIHandlerImpl());
		Resource resource = resourceSet.createResource(createCollectionURI(ModelPackage.Literals.PERSON));
		resource.getContents().add(author);
		resource.save(null);

		assertThat(personCollection.getCount(), is(1L));
		assertThat(resource.getURI().segmentCount(), is(3));
		assertThat(resource.getURI().segment(2), is(notNullValue()));
	}

	@Test
	public void tesetLoadAuthor()
	{
		BasicDBObject object = createAuthor("Stephen King");

		ResourceSet resourceSet = new ResourceSetImpl();
		EList<URIHandler> uriHandlers = resourceSet.getURIConverter().getURIHandlers();
		uriHandlers.add(0, new MongoDBURIHandlerImpl());
		Resource resource = resourceSet.getResource(createObjectURI(ModelPackage.Literals.PERSON, (ObjectId) object.get(ID_KEY)), true);
		assertThat(resource, is(notNullValue()));
		assertThat(resource.getContents().size(), is(1));
		assertThat(resource.getContents().get(0), is(instanceOf(Person.class)));
		Person author = (Person) resource.getContents().get(0);
		assertThat(author.getName(), is(object.get(ModelPackage.Literals.PERSON__NAME.getName())));
	}

	@Test
	public void testDeleteAuthor() throws IOException
	{
		BasicDBObject object = new BasicDBObject();
		object.put(ModelPackage.Literals.BOOK__TITLE.getName(), "Stephen King");
		personCollection.insert(object);
		assertThat(personCollection.getCount(), is(1L));

		ResourceSet resourceSet = new ResourceSetImpl();
		EList<URIHandler> uriHandlers = resourceSet.getURIConverter().getURIHandlers();
		uriHandlers.add(0, new MongoDBURIHandlerImpl());
		Resource resource = resourceSet.createResource(createObjectURI(ModelPackage.Literals.PERSON, (ObjectId) object.get(ID_KEY)));
		resource.delete(null);

		assertThat(personCollection.getCount(), is(0L));

	}

	@Test
	public void testSaveLibrary() throws IOException
	{
		ResourceSet resourceSet = new ResourceSetImpl();
		EList<URIHandler> uriHandlers = resourceSet.getURIConverter().getURIHandlers();
		uriHandlers.add(0, new MongoDBURIHandlerImpl());
		Library library = ModelFactory.eINSTANCE.createLibrary();

		Location location = ModelFactory.eINSTANCE.createLocation();
		location.setAddress("Wastelands");
		library.setLocation(location);

		Resource locationResource = resourceSet.createResource(createCollectionURI(ModelPackage.Literals.LOCATION));
		locationResource.getContents().add(location);
		locationResource.save(null);

		Person person = ModelFactory.eINSTANCE.createPerson();
		person.setName("Stephen King");

		Resource personResource = resourceSet.createResource(createCollectionURI(ModelPackage.Literals.PERSON));
		personResource.getContents().add(person);
		personResource.save(null);

		Book book = ModelFactory.eINSTANCE.createBook();
		book.setTitle("The Gunslinger");
		library.getBooks().add(book);
		book.getAuthors().add(person);

		Resource libraryResource = resourceSet.createResource(createCollectionURI(ModelPackage.Literals.LIBRARY));
		libraryResource.getContents().add(library);
		libraryResource.save(null);

		personResource.save(null);

		{
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			libraryResource.save(out, null);
			Resource libraryXMI = new XMIResourceFactoryImpl().createResource(URI.createURI("library.xmi"));
			libraryXMI.load(new ByteArrayInputStream(out.toByteArray()), null);
			Resource libraryMongo = new MongoDBResourceFactoryImpl().createResource(URI.createURI("library.mongo.xmi"));
			libraryMongo.load(new ByteArrayInputStream(out.toByteArray()), null);
			assertThat(EcoreUtil.equals(libraryXMI.getContents().get(0), libraryMongo.getContents().get(0)), is(true));
		}

		{
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			Map<Object, Object> options = new HashMap<Object, Object>();
			options.put(XMLResource.OPTION_BINARY, Boolean.TRUE);
			libraryResource.save(out, options);
			Resource libraryBinary = new BinaryResourceImpl(URI.createURI("library.binary"));
			libraryBinary.load(new ByteArrayInputStream(out.toByteArray()), null);
			Resource libraryMongo = new MongoDBResourceFactoryImpl().createResource(URI.createURI("library.mongo.binary"));
			libraryMongo.load(new ByteArrayInputStream(out.toByteArray()), options);
			assertThat(EcoreUtil.equals(libraryBinary.getContents().get(0), libraryMongo.getContents().get(0)), is(true));
		}

		{
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			Map<Object, Object> options = new HashMap<Object, Object>();
			options.put(XMIResource.OPTION_SUPPRESS_XMI, Boolean.TRUE);
			libraryResource.save(out, options);
			Resource libraryXML = new XMLResourceFactoryImpl().createResource(URI.createURI("library.xml"));
			libraryXML.load(new ByteArrayInputStream(out.toByteArray()), null);
			Resource libraryMongo = new MongoDBResourceFactoryImpl().createResource(URI.createURI("library.mongo.xml"));
			libraryMongo.load(new ByteArrayInputStream(out.toByteArray()), options);
			assertThat(EcoreUtil.equals(libraryXML.getContents().get(0), libraryMongo.getContents().get(0)), is(true));
		}

		assertThat(libraryCollection.getCount(), is(1L));
		assertThat(libraryResource.getURI().segmentCount(), is(3));
		assertThat(libraryResource.getURI().segment(2), is(notNullValue()));

		DBObject dbLibrary = libraryCollection.findOne();
		DBRef dbLocation = (DBRef) dbLibrary.get(ModelPackage.Literals.LIBRARY__LOCATION.getName());
		assertThat(dbLocation, is(notNullValue()));
		@SuppressWarnings("unchecked")
		List<DBObject> books = (List<DBObject>) libraryCollection.findOne().get(ModelPackage.Literals.LIBRARY__BOOKS.getName());
		assertThat(books, is(notNullValue()));
		assertThat(books.size(), is(1));
		DBObject dbBook = (DBObject) books.get(0);
		assertThat(dbBook, is(notNullValue()));
		assertThat((String) dbBook.get(ModelPackage.Literals.BOOK__TITLE.getName()), is(book.getTitle()));
		@SuppressWarnings("unchecked")
		List<DBRef> authors = (List<DBRef>) dbBook.get(ModelPackage.Literals.BOOK__AUTHORS.getName());
		assertThat(authors, is(notNullValue()));
		assertThat(authors.size(), is(1));

		DBObject dbPerson = personCollection.findOne();
		assertThat(dbPerson, is(notNullValue()));
		@SuppressWarnings("unchecked")
		List<DBObject> bookReferences = (List<DBObject>) dbPerson.get(ModelPackage.Literals.PERSON__BOOKS.getName());
		assertThat(bookReferences, is(notNullValue()));
		assertThat(bookReferences.size(), is(1));
	}

	@Test
	public void testLoadLibrary()
	{
		DBObject libraryObject = createLibrary("Wastelands");
		DBObject authorObject = createAuthor("Stephen King");
		ArrayList<DBObject> authors = new ArrayList<DBObject>();
		authors.add(authorObject);
		createBook(libraryObject, "The Gunslinger", authors);

		ResourceSet resourceSet = new ResourceSetImpl();
		EList<URIHandler> uriHandlers = resourceSet.getURIConverter().getURIHandlers();
		uriHandlers.add(0, new MongoDBURIHandlerImpl());
		Resource resource = resourceSet.getResource(createObjectURI(ModelPackage.Literals.LIBRARY, (ObjectId) libraryObject.get(ID_KEY)), true);
		assertThat(resource, is(notNullValue()));
		assertThat(resource.getContents().size(), is(1));

		Library library = (Library) resource.getContents().get(0);

		assertThat(library.getLocation(), is(notNullValue()));
		assertThat(library.getLocation().getAddress(), is("Wastelands"));

		assertThat(library.getBooks().size(), is(1));
		Book book = library.getBooks().get(0);
		assertThat(book.getTitle(), is("The Gunslinger"));
		assertThat(book.getAuthors().size(), is(1));
		Person author = book.getAuthors().get(0);
		assertThat(author.getName(), is("Stephen King"));
		assertThat(author.getBooks().size(), is(1));
		assertThat(author.getBooks().get(0), is(book));
	}

	@Test
	public void testUpdateAttribute() throws IOException
	{
		DBObject authorObject = createAuthor("Stephen King");

		ResourceSet resourceSet = new ResourceSetImpl();
		EList<URIHandler> uriHandlers = resourceSet.getURIConverter().getURIHandlers();
		uriHandlers.add(0, new MongoDBURIHandlerImpl());
		Resource resource = resourceSet.getResource(createObjectURI(ModelPackage.Literals.PERSON, (ObjectId) authorObject.get(ID_KEY)), true);
		Person author = (Person) resource.getContents().get(0);
		author.setName("Tom Clancy");
		resource.save(null);

		assertThat(personCollection.getCount(), is(1L));
		DBObject testObject = personCollection.findOne();
		assertThat((String) testObject.get(ModelPackage.Literals.PERSON__NAME.getName()), is(author.getName()));

	}

	@Test
	public void testSaveUpdateLoad() throws IOException
	{
		ResourceSet resourceSet = new ResourceSetImpl();
		EList<URIHandler> uriHandlers = resourceSet.getURIConverter().getURIHandlers();
		uriHandlers.add(0, new MongoDBURIHandlerImpl());
		Library library = ModelFactory.eINSTANCE.createLibrary();
		Resource libraryResource = resourceSet.createResource(createCollectionURI(ModelPackage.Literals.LIBRARY));
		libraryResource.getContents().add(library);
		libraryResource.save(null);

		Location location = ModelFactory.eINSTANCE.createLocation();
		location.setAddress("Austin, TX");
		Resource locationResource = resourceSet.createResource(createCollectionURI(ModelPackage.Literals.LOCATION));
		locationResource.getContents().add(location);
		locationResource.save(null);

		library.setLocation(location);
		libraryResource.save(null);
		assertThat(libraryCollection.getCount(), is(1L));

		Person author = ModelFactory.eINSTANCE.createPerson();
		author.setName("Tom Clancy");
		Resource authorResource = resourceSet.createResource(createCollectionURI(ModelPackage.Literals.PERSON));
		authorResource.getContents().add(author);
		authorResource.save(null);

		Book book1 = ModelFactory.eINSTANCE.createBook();
		book1.setTitle("The Hunt for Red October");
		book1.getAuthors().add(author);

		library.getBooks().add(book1);
		libraryResource.save(null);
		assertThat(libraryCollection.getCount(), is(1L));

		Book book2 = ModelFactory.eINSTANCE.createBook();
		book2.setTitle("The Cardinal of the Kremlin");
		book2.getAuthors().add(author);
		library.getBooks().add(book2);

		Book book3 = ModelFactory.eINSTANCE.createBook();
		book3.setTitle("Without Remorse");
		book3.getAuthors().add(author);
		library.getBooks().add(book3);

		libraryResource.save(null);
		assertThat(libraryCollection.getCount(), is(1L));
		authorResource.save(null);

		ObjectId libraryID = new ObjectId(libraryResource.getURI().segment(2));
		ResourceSet targetResourceSet = new ResourceSetImpl();
		uriHandlers = targetResourceSet.getURIConverter().getURIHandlers();
		uriHandlers.add(0, new MongoDBURIHandlerImpl());
		Resource targetLibraryResource = targetResourceSet.getResource(createObjectURI(ModelPackage.Literals.LIBRARY, libraryID), true);

		assertThat(targetLibraryResource, is(notNullValue()));
		assertThat(targetLibraryResource.getContents().size(), is(1));
		Library targetLibrary = (Library) targetLibraryResource.getContents().get(0);

		assertThat(targetLibrary.getLocation(), is(notNullValue()));
		assertThat(targetLibrary.getLocation().getAddress(), is(location.getAddress()));

		assertThat(targetLibrary.getBooks().size(), is(3));
		assertThat(targetLibrary.getBooks().get(0).getTitle(), is(book1.getTitle()));
		assertThat(targetLibrary.getBooks().get(0).getAuthors().size(), is(1));
		assertThat(targetLibrary.getBooks().get(0).getAuthors().get(0).getName(), is(author.getName()));

		assertThat(targetLibrary.getBooks().get(1).getTitle(), is(book2.getTitle()));
		assertThat(targetLibrary.getBooks().get(1).getAuthors().size(), is(1));
		assertThat(targetLibrary.getBooks().get(1).getAuthors().get(0).getName(), is(author.getName()));

		assertThat(targetLibrary.getBooks().get(2).getTitle(), is(book3.getTitle()));
		assertThat(targetLibrary.getBooks().get(2).getAuthors().size(), is(1));
		assertThat(targetLibrary.getBooks().get(2).getAuthors().get(0).getName(), is(author.getName()));

		ObjectId authorID = new ObjectId(authorResource.getURI().segment(2));
		Resource targetPersonResource = targetResourceSet.getResource(createObjectURI(ModelPackage.Literals.PERSON, authorID), true);
		assertThat(targetPersonResource, is(notNullValue()));
		assertThat(targetPersonResource.getContents().size(), is(1));
		Person targetAuthor = (Person) targetPersonResource.getContents().get(0);
		assertThat(targetAuthor.getBooks().size(), is(3));
		assertThat(targetAuthor.getBooks().get(0), is(sameInstance(targetLibrary.getBooks().get(0))));
		assertThat(targetAuthor.getBooks().get(1), is(sameInstance(targetLibrary.getBooks().get(1))));
		assertThat(targetAuthor.getBooks().get(2), is(sameInstance(targetLibrary.getBooks().get(2))));
	}

	@Test
	public void testExternalReference() throws IOException
	{
		ResourceSet resourceSet = new ResourceSetImpl();
		EList<URIHandler> uriHandlers = resourceSet.getURIConverter().getURIHandlers();
		uriHandlers.add(0, new MongoDBURIHandlerImpl());

		Person author = ModelFactory.eINSTANCE.createPerson();
		author.setName("Ed Merks");
		Resource authorResource = resourceSet.createResource(createCollectionURI(ModelPackage.Literals.PERSON));
		authorResource.getContents().add(author);

		URI bookURI = URI.createFileURI(temporaryFolder.newFile("book.xml").getAbsolutePath());
		Resource bookResource = resourceSet.createResource(bookURI);
		Book book = ModelFactory.eINSTANCE.createBook();
		book.setTitle("Eclipse Modeling Framework");
		book.getAuthors().add(author);
		bookResource.getContents().add(book);

		authorResource.save(null);
		bookResource.save(null);
		authorResource.save(null);

		ResourceSet targetResourceSet = new ResourceSetImpl();
		uriHandlers = targetResourceSet.getURIConverter().getURIHandlers();
		uriHandlers.add(0, new MongoDBURIHandlerImpl());

		ObjectId authorID = new ObjectId(authorResource.getURI().segment(2));
		Resource targetAuthorResource = targetResourceSet.getResource(createObjectURI(ModelPackage.Literals.PERSON, authorID), true);
		assertThat(targetAuthorResource, is(notNullValue()));
		assertThat(targetAuthorResource.getContents().size(), is(1));
		Person targetAuthor = (Person) targetAuthorResource.getContents().get(0);
		assertThat(targetAuthor.getBooks().size(), is(1));
		Book targetBook = targetAuthor.getBooks().get(0);
		assertThat(targetBook.getTitle(), is(book.getTitle()));
	}

	@Ignore
	@Test
	public void testLargeDatabase() throws IOException
	{
		ResourceSet resourceSet = new ResourceSetImpl();
		EList<URIHandler> uriHandlers = resourceSet.getURIConverter().getURIHandlers();
		uriHandlers.add(0, new MongoDBURIHandlerImpl());

		int numberObjects = 1000000;
		long startTime = System.currentTimeMillis();

		for (int i = 0; i < numberObjects; i++)
		{
			Person person = ModelFactory.eINSTANCE.createPerson();
			person.setName("Person " + i);
			Resource resource = resourceSet.createResource(createCollectionURI(ModelPackage.Literals.PERSON));
			resource.getContents().add(person);
			resource.save(null);

			resourceSet.getResources().clear();

			if ((i % (numberObjects / 100)) == 0)
				System.out.print(".");
		}

		System.out.println();

		long endTime = System.currentTimeMillis();
		System.out.println("Time to load " + numberObjects + " objects: " + (endTime - startTime) + "ms");

		startTime = System.currentTimeMillis();
		DBObject person = personCollection.findOne(new BasicDBObject("name", "Person 203500"));
		assertThat(person, is(notNullValue()));
		endTime = System.currentTimeMillis();
		System.out.println("Time to find: " + (endTime - startTime) + "ms");
	}

	private BasicDBObject createAuthor(String name)
	{
		long count = personCollection.count();

		BasicDBObject object = new BasicDBObject();
		object.put("_ePackage", ModelPackage.eINSTANCE.getNsURI());
		object.put("_eClass", ModelPackage.Literals.PERSON.getName());
		object.put(ModelPackage.Literals.PERSON__NAME.getName(), name);

		personCollection.insert(object);
		assertThat(personCollection.getCount(), is(count + 1));
		return object;
	}

	private DBObject createBook(DBObject library, String title, List<DBObject> authors)
	{
		BasicDBObject object = new BasicDBObject();
		object.put("_ePackage", ModelPackage.eINSTANCE.getNsURI());
		object.put("_eClass", ModelPackage.Literals.BOOK.getName());
		object.put(ModelPackage.Literals.BOOK__TITLE.getName(), title);

		ArrayList<DBRef> authorsReferences = new ArrayList<DBRef>();

		for (DBObject author : authors)
			authorsReferences.add(new DBRef(db, ModelPackage.Literals.PERSON.getName(), author.get(ID_KEY)));

		object.put(ModelPackage.Literals.BOOK__AUTHORS.getName(), authorsReferences);

		@SuppressWarnings("unchecked")
		List<DBObject> books = (List<DBObject>) library.get(ModelPackage.Literals.LIBRARY__BOOKS.getName());

		if (books == null)
		{
			books = new ArrayList<DBObject>();
			library.put(ModelPackage.Literals.LIBRARY__BOOKS.getName(), books);
		}

		books.add(object);
		libraryCollection.update(new BasicDBObject(ID_KEY, library.get(ID_KEY)), library);

		for (DBObject author : authors)
		{
			@SuppressWarnings("unchecked")
			ArrayList<DBObject> bookReferences = (ArrayList<DBObject>) author.get(ModelPackage.Literals.PERSON__BOOKS.getName());

			if (bookReferences == null)
			{
				bookReferences = new ArrayList<DBObject>();
				author.put(ModelPackage.Literals.PERSON__BOOKS.getName(), bookReferences);
			}

			BasicDBObject proxy = new BasicDBObject();
			proxy.put("_eProxyURI", "mongo://localhost/test/Library/" + library.get("_id") + "#//@books." + (bookReferences.size()));
			proxy.put("_ePackage", ModelPackage.eINSTANCE.getNsURI());
			proxy.put("_eClass", ModelPackage.Literals.BOOK.getName());
			bookReferences.add(proxy);
			personCollection.update(new BasicDBObject(ID_KEY, author.get(ID_KEY)), author);
		}

		return object;
	}

	private BasicDBObject createLibrary(String location)
	{
		long locationCount = locationCollection.count();
		long libraryCount = libraryCollection.count();

		BasicDBObject locationObject = new BasicDBObject();
		locationObject.put("_ePackage", ModelPackage.eINSTANCE.getNsURI());
		locationObject.put("_eClass", ModelPackage.Literals.LOCATION.getName());
		locationObject.put(ModelPackage.Literals.LOCATION__ADDRESS.getName(), location);

		locationCollection.insert(locationObject);
		assertThat(locationCollection.getCount(), is(locationCount + 1));

		BasicDBObject libraryObject = new BasicDBObject();
		libraryObject.put("_ePackage", ModelPackage.eINSTANCE.getNsURI());
		libraryObject.put("_eClass", ModelPackage.Literals.LIBRARY.getName());
		libraryObject.put(ModelPackage.Literals.LIBRARY__LOCATION.getName(), new DBRef(db, ModelPackage.Literals.LOCATION.getName(), locationObject.get(ID_KEY)));

		libraryCollection.insert(libraryObject);
		assertThat(libraryCollection.getCount(), is(libraryCount + 1));
		return libraryObject;
	}

	private URI createCollectionURI(EClass eClass)
	{
		return URI.createURI("mongo://localhost/test/" + eClass.getName());
	}

	private URI createObjectURI(EClass eClass, ObjectId id)
	{
		return URI.createURI("mongo://localhost/test/" + eClass.getName() + "/" + id);
	}

	private static final String ID_KEY = "_id";

	private DBCollection personCollection;
	private DBCollection libraryCollection;
	private DBCollection locationCollection;
	private DB db;

	private Mongo mongo;
}
