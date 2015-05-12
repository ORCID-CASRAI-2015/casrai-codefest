package com.elsevier.casrai.funding_results.repeater;

import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.bson.Document;
import org.bson.types.ObjectId;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.mongodb.BasicDBObject;
import com.mongodb.MongoClient;
import com.mongodb.WriteConcern;
import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.DeleteOneModel;
import com.mongodb.client.model.InsertOneModel;
import com.mongodb.client.model.WriteModel;

public class CasraiStorageImpl implements CasraiStorage {
	private String db;
	private String col;

	private static final ReentrantReadWriteLock resumptionTokenLock = new ReentrantReadWriteLock();

	private MongoCollection<Document> collection;

	// ------------------------------------------- Public Methods -------------------------------------------

	public void initialize() {
		final MongoClient mongoClient = new MongoClient();
		mongoClient.setWriteConcern(WriteConcern.JOURNALED);
		final MongoDatabase database = mongoClient.getDatabase(db);
		collection = database.getCollection(col);

		collection.createIndex(new BasicDBObject("_type", 1));
		collection.createIndex(new BasicDBObject("Funds Request.Reference ID", 1));

		final Document resumptionTokensDocument = collection.find(new BasicDBObject("_type", "ResumptionTokens")).first();
		if(resumptionTokensDocument == null) {
			final Document document = new Document("_type", "ResumptionTokens");
			document.put("tokens", Lists.newArrayList());
			collection.insertOne(document);
		}
	}

	@SuppressWarnings("unchecked")
	@Override
	public String getResumptionToken(String url) {
		resumptionTokenLock.readLock().lock();
		try {
			final Document resumptionTokensDocument = collection.find(new BasicDBObject("_type", "ResumptionTokens")).first();
			List<Document> tokens = (List<Document>) resumptionTokensDocument.get("tokens");
			if(tokens != null) {
				for (Document token : tokens) {
					if (url.equals(token.get("url")))
						return (String) token.get("token");
				}
			}
			return null;
		} finally {
			resumptionTokenLock.readLock().unlock();
		}
	}

	@SuppressWarnings("unchecked")
	@Override
	public void setResumptionToken(String url, String resumptionToken) {
		resumptionTokenLock.writeLock().lock();
		try {
			final Document resumptionTokensDocument = collection.find(new BasicDBObject("_type", "ResumptionTokens")).first();
			List<Document> tokens = (List<Document>) resumptionTokensDocument.get("tokens");
			if(tokens == null) {
				tokens = Lists.newArrayList();
				resumptionTokensDocument.put("tokens", tokens);
			}
			boolean found = false;
			for (Document token : tokens) {
				if(url.equals(token.get("url"))) {
					found = true;
					token.put("token", resumptionToken);
				}
			}
			if(!found) {
				final Document document = new Document();
				document.put("url", url);
				document.put("token", resumptionToken);
				tokens.add(document);
			}

			collection.findOneAndReplace(new BasicDBObject("_type", "ResumptionTokens"), resumptionTokensDocument);
		} finally {
			resumptionTokenLock.writeLock().unlock();
		}
	}

	@Override
	public List<Map<String, Object>> getAwards(String lastObjectId, int noItemsToReturn) {
		final FindIterable<Document> finder = collection.find(new BasicDBObject("_type", "award"));
		if(lastObjectId != null)
			finder.filter(new BasicDBObject("_id", new BasicDBObject("$gt", new ObjectId(lastObjectId))));
		finder.sort(new BasicDBObject("_id", 1));
		finder.limit(noItemsToReturn);
		final MongoCursor<Document> it = finder.iterator();
		final List<Map<String, Object>> ret = Lists.newArrayList();
		while(it.hasNext())
			ret.add(it.next());
		return ret;
	}

	@Override
	public void saveAwards(List<Map<String, Object>> awards) {
		final List<WriteModel<Document>> operations = Lists.newArrayList();
		awards.forEach(a -> {
			operations.add(new DeleteOneModel<>(getId(a)));
			operations.add(new InsertOneModel<>(translateToDocument(a)));
		});
		collection.bulkWrite(operations);
	}

	@Override
	public Map<String, Object> getStatus() {
		// Generate last entries
		final FindIterable<Document> finder = collection.find(new BasicDBObject("_type", "award"));
		finder.sort(new BasicDBObject("_id", -1));
		finder.limit(5);
		final MongoCursor<Document> it = finder.iterator();
		final List<Map<String, Object>> lastEntries = Lists.newArrayList();
		while(it.hasNext())
			lastEntries.add(it.next());

		// Generate count
		final long count = collection.count(new BasicDBObject("_type", "award"));

		// Build result
		final Map<String, Object> ret = Maps.newLinkedHashMap();
		ret.put("count", count);
		ret.put("lastEntries", lastEntries);

		return ret;
	}

	// ------------------------------------------- Private parts -------------------------------------------

	@SuppressWarnings("unchecked")
	private Document getId(Map<String, Object> award) {
		final Map<String, Object> funds = (Map<String, Object>) award.get("Funds Request");
		return new Document("Funds Request", new Document("Reference ID", funds.get("Reference ID")));
	}

	private Document translateToDocument(Map<String, Object> award) {
		final Document document = new Document(award);
		document.put("_type", "award");
		return document;
	}

	// ------------------------------------------- Getters/Setters -------------------------------------------

	public void setDb(String db) {
		this.db = db;
	}

	public void setCol(String col) {
		this.col = col;
	}
}
